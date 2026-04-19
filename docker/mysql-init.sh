#!/bin/sh
set -eu

# wait until mysql is available
until mysqladmin ping -h mysql --silent; do
  sleep 1
done

# create admin user if missing and grant privileges
mysql -hmysql -uroot -p"$DB_ROOT_PASSWORD" -e "CREATE USER IF NOT EXISTS 'finance_admin'@'%' IDENTIFIED BY 'root'; GRANT ALL PRIVILEGES ON *.* TO 'finance_admin'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"

# run schema if present (don't fail if it errors)
if [ -f /tmp/schema.sql ]; then
  mysql -hmysql -uroot -p"$DB_ROOT_PASSWORD" "$DB_DATABASE" < /tmp/schema.sql || true
fi

exit 0
