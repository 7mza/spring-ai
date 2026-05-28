FROM grafana/grafana:latest
USER root
RUN apk update && apk upgrade && apk add jq && apk cache clean && rm -rf /var/cache/apk/*
USER grafana
RUN mkdir -p /var/lib/grafana/dashboards/
# place/download dashboards direclty in default dir provider
COPY ./spring_ai_dashboard.json /var/lib/grafana/dashboards/
RUN wget --no-check-certificate -O /var/lib/grafana/dashboards/19004.json https://grafana.com/api/dashboards/19004/revisions/latest/download
# force any dynamic datasource to default datasource uid
RUN sed -i 's/${DS_PROMETHEUS}/prometheus_uid/g' /var/lib/grafana/dashboards/*.json
RUN sed -i 's/${DS_PROM}/prometheus_uid/g' /var/lib/grafana/dashboards/*.json
