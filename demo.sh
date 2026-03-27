#!/usr/bin/env bash
set -euo pipefail

# ── Colors ──────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

GRAFANA_URL="http://localhost:3000"
GATEWAY_URL="http://localhost:8080"
CONTROL_PANEL_URL="http://localhost:8082"
DASHBOARD_URL="${GRAFANA_URL}/d/gateway-dashboard?orgId=1&refresh=5s&from=now-5m&to=now"

# ── Functions ───────────────────────────────────────────
print_banner() {
  echo ""
  echo -e "${BOLD}${CYAN}"
  echo "  ╔═══════════════════════════════════════════════╗"
  echo "  ║   Multiplexing Gateway — Live Demo            ║"
  echo "  ║   TCP Multiplexing + Backpressure + Recovery  ║"
  echo "  ╚═══════════════════════════════════════════════╝"
  echo -e "${NC}"
}

annotate() {
  local text="$1"
  curl -s -X POST "${GRAFANA_URL}/api/annotations" \
    -H "Content-Type: application/json" \
    -d "{\"text\":\"${text}\",\"tags\":[\"scenario\"]}" > /dev/null 2>&1 || true
}

wait_for_services() {
  echo -ne "  ${YELLOW}Waiting for services"
  for i in $(seq 1 30); do
    if curl -sf "${GATEWAY_URL}/actuator/health" > /dev/null 2>&1; then
      echo -e " Ready!${NC}"
      return 0
    fi
    echo -n "."
    sleep 2
  done
  echo -e " FAILED${NC}"
  echo -e "  ${RED}Gateway is not responding. Run 'docker compose up -d' first.${NC}"
  exit 1
}

run_scenario() {
  local scenario="$1"
  local description="$2"
  local watch_hint="$3"

  echo ""
  echo -e "${BOLD}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}   SCENARIO: ${scenario}${NC}"
  echo -e "${CYAN}   ${description}${NC}"
  echo -e "${YELLOW}   Watch: ${watch_hint}${NC}"
  echo -e "${BOLD}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""

  annotate "▶ ${scenario} START"
  echo -e "  ${GREEN}▶ ${scenario} START${NC}"

  docker compose run --rm loadgen "${scenario}" 2>/dev/null

  annotate "■ ${scenario} END"
  echo -e "  ${GREEN}■ ${scenario} END${NC}"
  echo ""
  echo -e "  ${CYAN}Recovery window — watch the dashboard...${NC}"
  sleep 15
}

open_dashboard() {
  if command -v open &> /dev/null; then
    open "${DASHBOARD_URL}" 2>/dev/null
  elif command -v xdg-open &> /dev/null; then
    xdg-open "${DASHBOARD_URL}" 2>/dev/null
  fi
  echo -e "  ${CYAN}Dashboard: ${DASHBOARD_URL}${NC}"
}

print_done() {
  echo ""
  echo -e "${BOLD}${GREEN}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${BOLD}${GREEN}   DEMO COMPLETE${NC}"
  echo -e "${BOLD}${GREEN}  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""
  echo -e "  Dashboard: ${DASHBOARD_URL}"
  echo ""
  echo -e "  ${CYAN}Control Panel: ${CONTROL_PANEL_URL}${NC}"
  echo -e "  ${CYAN}Grafana:       ${DASHBOARD_URL}${NC}"
  echo ""
  echo -e "  ${CYAN}Optional scenarios:${NC}"
  echo -e "    ./demo.sh burst          # Burst only"
  echo -e "    ./demo.sh slowdown       # Slowdown only"
  echo -e "    ./demo.sh session-drop   # TCP disconnect + reconnect"
  echo -e "    ./demo.sh out-of-order   # Response reordering"
  echo -e "    ./demo.sh all            # Run all 4 scenarios"
  echo ""
}

# ── Scenario Definitions ────────────────────────────────
run_burst() {
  run_scenario "BURST" \
    "2000 req/s flood for 10 seconds (20x normal)" \
    "Latency stays flat. BUSY absorbs excess traffic."
}

run_slowdown() {
  run_scenario "SLOWDOWN" \
    "Upstream latency: 30ms -> 100ms -> 200ms -> 300ms -> recovery" \
    "Inflight climbs to limit. BUSY prevents cascade failure."
}

run_session_drop() {
  run_scenario "SESSION_DROP" \
    "TCP disconnect injection every 10 seconds" \
    "Pending fail-fast, reconnect count, recovery time."
}

run_out_of_order() {
  run_scenario "OUT_OF_ORDER" \
    "30% of responses arrive out of order" \
    "All correlationIds still match correctly."
}

# ── Main ────────────────────────────────────────────────
print_banner
wait_for_services

case "${1:-default}" in
  default)
    open_dashboard
    echo -e "  ${YELLOW}Establishing baseline metrics (5s)...${NC}"
    sleep 5
    run_burst
    run_slowdown
    print_done
    ;;
  burst)
    run_burst
    print_done
    ;;
  slowdown)
    run_slowdown
    print_done
    ;;
  session-drop)
    run_session_drop
    print_done
    ;;
  out-of-order)
    run_out_of_order
    print_done
    ;;
  all)
    open_dashboard
    echo -e "  ${YELLOW}Establishing baseline metrics (5s)...${NC}"
    sleep 5
    run_burst
    run_slowdown
    run_session_drop
    run_out_of_order
    print_done
    ;;
  *)
    echo "Usage: ./demo.sh [burst|slowdown|session-drop|out-of-order|all]"
    echo "       ./demo.sh           # default: burst + slowdown"
    exit 1
    ;;
esac
