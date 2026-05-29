FROM jaegertracing/jaeger:latest
USER root
RUN apk update && apk upgrade && apk add jq && apk cache clean && rm -rf /var/cache/apk/*
