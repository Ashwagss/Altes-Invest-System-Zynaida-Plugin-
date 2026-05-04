#!/bin/bash
echo "=== ZynaidaInvest Build ==="
cd "$(dirname "$0")"
mvn clean package -DskipTests
echo ""
if [ -f target/ZynaidaInvest-*.jar ]; then
    echo "Build erfolgreich!"
    echo "JAR: $(ls target/ZynaidaInvest-*.jar)"
else
    echo "Build fehlgeschlagen!"
fi
