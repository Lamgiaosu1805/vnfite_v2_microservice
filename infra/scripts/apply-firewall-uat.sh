#!/usr/bin/env bash
set -euo pipefail

# UAT/live host firewall for VNFITE API nodes.
# Keeps SSH, HTTP(S), and the public API gateway open; blocks direct service/infra ports.

ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 7080/tcp

ufw deny 8081:8090/tcp
ufw deny 6379/tcp
ufw deny 9092/tcp
ufw deny 2181/tcp
ufw deny 8989/tcp

ufw --force enable
ufw status verbose
