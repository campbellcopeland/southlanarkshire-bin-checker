#!/bin/bash

echo "🛑 Checking for existing process on port 8080..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || true
sleep 1

echo "🗑️  Starting Bin Collection Checker..."
echo "📍 Access at: http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# /home/campbell/.maven/maven-3.9.12/bin/mvn spring-boot:run
mvn spring-boot:run