#!/bin/bash
# Se ejecuta DENTRO de la EC2 (lo envía GitHub Actions por AWS Systems Manager).
# Descarga la imagen desde ECR y reemplaza el contenedor del servicio.
#
# Uso: deploy-ec2.sh <nombre> <imagen> <puerto-host> <puerto-contenedor> <ruta-health> <region> [secret-arn] [bucket] [key-wallet]
set -euo pipefail

NOMBRE="$1"; IMAGEN="$2"; PUERTO_HOST="$3"; PUERTO_CONTENEDOR="$4"; HEALTH="$5"; REGION="$6"
SECRET_ARN="${7:-}"; BUCKET="${8:-}"; WALLET_KEY="${9:-}"
BASE=/opt/agora
mkdir -p "$BASE"

echo ">> Login en ECR"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${IMAGEN%%/*}" >/dev/null

DOCKER_ARGS=()
if [[ -n "$SECRET_ARN" ]]; then
  echo ">> Descargando el Wallet de Oracle desde S3"
  rm -rf "$BASE/wallet.tmp" && mkdir -p "$BASE/wallet.tmp"
  aws s3 cp "s3://$BUCKET/$WALLET_KEY" "$BASE/wallet.zip" --region "$REGION" --only-show-errors
  python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$BASE/wallet.zip" "$BASE/wallet.tmp"
  # Si el zip trae una carpeta interna, se usa esa
  if [[ ! -f "$BASE/wallet.tmp/tnsnames.ora" ]]; then
    interna="$(dirname "$(find "$BASE/wallet.tmp" -name tnsnames.ora | head -1)")"
    mv "$interna" "$BASE/wallet.tmp2" && rm -rf "$BASE/wallet.tmp" && mv "$BASE/wallet.tmp2" "$BASE/wallet.tmp"
  fi
  rm -rf "$BASE/wallet" && mv "$BASE/wallet.tmp" "$BASE/wallet" && rm -f "$BASE/wallet.zip"
  chmod -R a+rX "$BASE/wallet"

  echo ">> Leyendo credenciales de Secrets Manager (no se imprimen)"
  aws secretsmanager get-secret-value --secret-id "$SECRET_ARN" --region "$REGION" \
    --query SecretString --output text \
    | python3 -c "import json,sys; [print(f'{k}={v}') for k,v in json.load(sys.stdin).items()]" > "$BASE/$NOMBRE.env"
  chmod 600 "$BASE/$NOMBRE.env"
  DOCKER_ARGS+=(--env-file "$BASE/$NOMBRE.env" -e DB_WALLET_PATH=/app/wallet -e "CORS_ORIGENES=*" -v "$BASE/wallet:/app/wallet:ro")
fi

echo ">> Descargando $IMAGEN"
docker pull -q "$IMAGEN"

echo ">> Reemplazando el contenedor $NOMBRE"
docker rm -f "$NOMBRE" >/dev/null 2>&1 || true
docker run -d --name "$NOMBRE" --restart unless-stopped -p "$PUERTO_HOST:$PUERTO_CONTENEDOR" "${DOCKER_ARGS[@]}" "$IMAGEN" >/dev/null

echo ">> Esperando que responda http://localhost:$PUERTO_HOST$HEALTH"
for i in $(seq 1 36); do
  if curl -fsS "http://localhost:$PUERTO_HOST$HEALTH" >/dev/null 2>&1; then
    echo "OK: $NOMBRE desplegado y respondiendo"
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
  fi
  sleep 5
done

echo "ERROR: $NOMBRE no respondió a tiempo. Últimos logs:"
docker logs --tail 80 "$NOMBRE" 2>&1 | grep -viE "password|secret" || true
exit 1
