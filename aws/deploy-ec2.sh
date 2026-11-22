#!/bin/bash
# Se ejecuta DENTRO de la EC2 (lo envía GitHub Actions por AWS Systems Manager).
# Descarga la imagen desde ECR y reemplaza el contenedor del servicio con rollback:
# el contenedor anterior se conserva hasta que el nuevo responde; si falla, se restaura.
#
# Uso: deploy-ec2.sh <nombre> <imagen> <puerto-host> <puerto-contenedor> <ruta-health> <region> [secret-arn] [bucket] [key-wallet]
set -Eeuo pipefail

NOMBRE="${1:?nombre requerido}"; IMAGEN="${2:?imagen requerida}"
PUERTO_HOST="${3:?puerto host requerido}"; PUERTO_CONTENEDOR="${4:?puerto contenedor requerido}"
HEALTH="${5:?ruta health requerida}"; REGION="${6:?region requerida}"
SECRET_ARN="${7:-}"; BUCKET="${8:-}"; WALLET_KEY="${9:-}"
BASE=/opt/foro
mkdir -p "$BASE" && chmod 700 "$BASE"

if [[ ! "$NOMBRE" =~ ^[a-z0-9][a-z0-9-]*$ || ! "$PUERTO_HOST" =~ ^[0-9]+$ || ! "$PUERTO_CONTENEDOR" =~ ^[0-9]+$ \
  || ! "$HEALTH" =~ ^/ || ! "$REGION" =~ ^[a-z0-9-]+$ ]]; then
  echo "Argumentos inválidos." >&2; exit 2
fi

# Credenciales de Docker temporales (no quedan guardadas en el disco de la EC2)
export DOCKER_CONFIG="$(mktemp -d /tmp/foro-docker.XXXXXX)"
ENV_FILE=""
limpiar() { rm -rf -- "$DOCKER_CONFIG"; [[ -n "$ENV_FILE" ]] && rm -f -- "$ENV_FILE"; return 0; }

echo ">> Descargando $IMAGEN"
REGISTRO="${IMAGEN%%/*}"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRO" >/dev/null
docker pull -q "$IMAGEN"
docker logout "$REGISTRO" >/dev/null 2>&1 || true

DOCKER_ARGS=()
if [[ -n "$SECRET_ARN" ]]; then
  echo ">> Descargando el Wallet de Oracle desde S3"
  WALLET_DIR="$BASE/wallet-$NOMBRE"
  TMP_DIR="$(mktemp -d "$BASE/wallet.XXXXXX")"
  aws s3 cp "s3://$BUCKET/$WALLET_KEY" "$TMP_DIR/wallet.zip" --region "$REGION" --only-show-errors
  # Descomprime y devuelve la carpeta que contiene tnsnames.ora (por si el ZIP trae una carpeta interna)
  ORIGEN="$(python3 - "$TMP_DIR/wallet.zip" "$TMP_DIR/out" <<'PY'
import os, sys, zipfile
zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])
for raiz, _, archivos in os.walk(sys.argv[2]):
    if "tnsnames.ora" in archivos:
        print(raiz)
        break
else:
    raise SystemExit("El ZIP no contiene tnsnames.ora: sube el Wallet original de Oracle.")
PY
)"
  rm -rf "$WALLET_DIR" && mv "$ORIGEN" "$WALLET_DIR" && rm -rf "$TMP_DIR"
  chmod -R a+rX "$WALLET_DIR"

  echo ">> Leyendo credenciales de Secrets Manager (no se imprimen)"
  ENV_FILE="$(mktemp /tmp/foro-env.XXXXXX)"
  aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" --region "$REGION" \
    --query SecretString --output text \
    | python3 -c '
import json, sys
s = json.load(sys.stdin)
alias = s.get("DB_URL") or s.get("DB_TNS_NAME")
# (variable de la app, clave en el secreto, valor)
valores = [("DB_TNS_NAME", "DB_URL", alias),
           ("DB_USERNAME", "DB_USERNAME", s.get("DB_USERNAME")),
           ("DB_PASSWORD", "DB_PASSWORD", s.get("DB_PASSWORD"))]
with open(sys.argv[1], "w", encoding="utf-8") as out:
    for variable, clave_secreto, valor in valores:
        if not isinstance(valor, str) or not valor or "\n" in valor:
            raise SystemExit("Al secreto le falta un valor valido para " + clave_secreto)
        out.write(variable + "=" + valor + "\n")
    # 3 microservicios contra la misma Autonomous DB gratuita: pool chico para no saturarla
    out.write("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3\n")
    out.write("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=60000\n")
' "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  DOCKER_ARGS+=(--env-file "$ENV_FILE" -e DB_WALLET_PATH=/app/wallet -e "CORS_ORIGENES=*" -v "$WALLET_DIR:/app/wallet:ro")
fi

# ---------- Reemplazo con rollback ----------
RESPALDO=""
NUEVO_CREADO=false
al_salir() {
  estado=$?
  trap - EXIT
  if (( estado != 0 )); then
    if [[ "$NUEVO_CREADO" == true ]] && docker inspect "$NOMBRE" >/dev/null 2>&1; then
      echo ">> Últimos logs del contenedor nuevo:" >&2
      docker logs --tail 80 "$NOMBRE" 2>&1 | grep -viE "password|secret" >&2 || true
      docker rm -f "$NOMBRE" >/dev/null
    fi
    if [[ -n "$RESPALDO" ]] && docker inspect "$RESPALDO" >/dev/null 2>&1; then
      docker rename "$RESPALDO" "$NOMBRE" && docker start "$NOMBRE" >/dev/null
      echo "ROLLBACK: el despliegue falló y se restauró la versión anterior de $NOMBRE." >&2
    fi
  fi
  limpiar
  exit "$estado"
}
trap al_salir EXIT

if docker inspect "$NOMBRE" >/dev/null 2>&1; then
  RESPALDO="$NOMBRE-respaldo-$(date +%s)"
  docker rename "$NOMBRE" "$RESPALDO"
  docker stop "$RESPALDO" >/dev/null || true
fi

echo ">> Iniciando $NOMBRE en 127.0.0.1:$PUERTO_HOST (Nginx lo publica en el puerto 80)"
NUEVO_CREADO=true
docker run -d --name "$NOMBRE" --restart unless-stopped \
  -p "127.0.0.1:$PUERTO_HOST:$PUERTO_CONTENEDOR" "${DOCKER_ARGS[@]}" "$IMAGEN" >/dev/null
[[ -n "$ENV_FILE" ]] && rm -f -- "$ENV_FILE" && ENV_FILE=""

echo ">> Esperando que responda http://127.0.0.1:$PUERTO_HOST$HEALTH"
for i in $(seq 1 60); do
  if curl -fsS --max-time 5 "http://127.0.0.1:$PUERTO_HOST$HEALTH" >/dev/null 2>&1; then
    [[ -n "$RESPALDO" ]] && docker rm "$RESPALDO" >/dev/null
    RESPALDO=""
    docker image prune -f >/dev/null 2>&1 || true
    echo "OK: $NOMBRE desplegado ($IMAGEN)"
    exit 0
  fi
  sleep 5
done

echo "ERROR: $NOMBRE no respondió en 5 minutos." >&2
exit 1
