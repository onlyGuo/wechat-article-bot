#!/usr/bin/env sh
set -eu

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <registry/repository> <version> [platforms]" >&2
  echo "Example: $0 ghcr.io/acme/wechat-article-bot 1.0.0 linux/amd64,linux/arm64" >&2
  exit 64
fi

repository=$1
version=$2
platforms=${3:-linux/amd64,linux/arm64}
engine=${CONTAINER_ENGINE:-auto}

case "$repository" in
  */*) ;;
  *) echo "Repository must include a registry namespace, for example ghcr.io/acme/wechat-article-bot" >&2; exit 64 ;;
esac

if [ "$engine" = auto ]; then
  if command -v container >/dev/null 2>&1; then
    engine=apple
  elif command -v docker >/dev/null 2>&1; then
    engine=docker
  else
    echo "Neither Apple container nor Docker is installed" >&2
    exit 69
  fi
fi

if [ "$engine" = apple ]; then
  case "$platforms" in
    linux/amd64,linux/arm64|linux/arm64,linux/amd64) set -- --arch amd64 --arch arm64 ;;
    linux/amd64) set -- --arch amd64 ;;
    linux/arm64) set -- --arch arm64 ;;
    *) echo "Apple container supports this script for linux/amd64 and/or linux/arm64" >&2; exit 64 ;;
  esac
  container build "$@" \
    --build-arg "APP_VERSION=$version" \
    --tag "$repository:$version" \
    --file Dockerfile \
    .
  container image tag "$repository:$version" "$repository:latest"
  container image push "$repository:$version"
  container image push "$repository:latest"
elif [ "$engine" = docker ]; then
  docker buildx build \
    --platform "$platforms" \
    --build-arg "APP_VERSION=$version" \
    --tag "$repository:$version" \
    --tag "$repository:latest" \
    --provenance=true \
    --sbom=true \
    --push \
    .
else
  echo "CONTAINER_ENGINE must be auto, apple, or docker" >&2
  exit 64
fi

echo "Published $repository:$version and $repository:latest for $platforms with $engine"
