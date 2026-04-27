FROM php:8.2-cli

# Install Postgres PDO extension
RUN apt-get update && apt-get install -y libpq-dev \
    && docker-php-ext-install pdo pdo_pgsql

# Create app directory
WORKDIR /app

# Copy source code
COPY . .

# Run the built-in PHP server on port 80
# Railway will automatically route traffic to this port
CMD ["php", "-S", "0.0.0.0:80", "index.php"]
