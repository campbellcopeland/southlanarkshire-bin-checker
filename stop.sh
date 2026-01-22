#!/bin/bash
# Stop the Bin Collection Checker application

echo "🛑 Stopping Bin Collection Checker..."

# Kill process on port 8080
lsof -ti:8080 | xargs kill -9 2>/dev/null || true

echo "✅ Application stopped"
