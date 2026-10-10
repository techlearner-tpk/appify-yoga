.PHONY: start stop test integration-test e2e-test load-test clean
start:
	docker compose up --build -d
stop:
	docker compose down
test:
	docker build --target build -t appify-yoga-test backend
	docker run --rm appify-yoga-test mvn test
	docker build -f frontend/Dockerfile.test -t appify-yoga-frontend-test frontend
	docker run --rm appify-yoga-frontend-test
integration-test:
	docker compose up -d --build
	docker run --rm --network appify-yoga_default -v $(CURDIR)/scripts:/scripts:ro -e API_URL=http://backend-api:8080 -e WEB_URL=http://frontend:3000 --entrypoint sh curlimages/curl:8.12.0 /scripts/smoke.sh
	docker run --rm --network appify-yoga_default -v $(CURDIR)/scripts:/scripts:ro -e API_URL=http://backend-api:8080 -e DEMO_PASSWORD=$${DEMO_PASSWORD:-DemoPass123!} python:3.12-alpine python /scripts/integration_flow.py
	docker run --rm --network appify-yoga_default -v $(CURDIR)/scripts:/scripts:ro -e API_URL=http://backend-api:8080 -e DEMO_PASSWORD=$${DEMO_PASSWORD:-DemoPass123!} python:3.12-alpine python /scripts/session_security_flow.py
	docker run --rm --network appify-yoga_default -v $(CURDIR)/scripts:/scripts:ro -e API_URL=http://backend-api:8080 -e DEMO_PASSWORD=$${DEMO_PASSWORD:-DemoPass123!} python:3.12-alpine python /scripts/content_smoke.py
	python3 scripts/provider_security_flow.py
e2e-test:
	docker compose up -d --build
	docker build -f frontend/Dockerfile.e2e -t appify-yoga-e2e frontend
	docker run --rm --network appify-yoga_default -e BASE_URL=http://frontend:3000 appify-yoga-e2e
load-test:
	docker run --rm --add-host host.docker.internal:host-gateway -e VUS -e WARMUP -e DURATION -e COOLDOWN -e EMAIL -e EMAIL_PREFIX -e PASSWORD -e SESSION_ID -v $(CURDIR)/load:/scripts grafana/k6:0.55.0 run /scripts/primary-flow.js
clean:
	docker compose down -v
