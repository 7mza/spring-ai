FROM qdrant/qdrant:latest
USER root
RUN apt-get update && apt-get install curl -y
RUN apt-get autoremove --purge && rm -rf /var/lib/apt/lists/*
