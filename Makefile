.PHONY: build test integration e2e perf down
build:
	mvn -B -ntp package -DskipUTs=true -DskipITs=true
test:
	mvn -B -ntp verify -DskipITs=true
integration:
	mvn -B -ntp verify
e2e:
	docker compose up -d --build --wait --wait-timeout 180
	mvn -B -ntp -Pe2e -pl e2e-tests test
perf:
	docker compose up -d --build --wait --wait-timeout 180
	bash scripts/perf.sh
down:
	docker compose down -v
