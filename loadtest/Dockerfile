FROM python:3.12-slim
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY mock_server.py .
EXPOSE 9090
CMD ["python3", "mock_server.py"]
