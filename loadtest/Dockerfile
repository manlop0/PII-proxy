FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir aiohttp
COPY mock_server.py .
EXPOSE 9090
CMD ["python3", "mock_server.py"]
