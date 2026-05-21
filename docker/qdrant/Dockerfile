FROM qdrant/qdrant:latest
USER root
#RUN apt-get update && apt-get full-upgrade -y && apt-get install curl jq -y
RUN apt-get update && apt-get install curl jq -y
RUN apt-get autoremove --purge && rm -rf /var/lib/apt/lists/*
