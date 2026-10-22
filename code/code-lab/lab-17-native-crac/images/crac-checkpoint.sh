#!/usr/bin/env bash
# menu-service restored from a CRaC checkpoint. Needs a JDK with CRaC (Azul Zulu or BellSoft Liberica builds) and
# CRIU, which needs privileges in the container and a kernel it supports. Chapter 60's VM could not run CRIU
# ("vdso: Unexpected rt vDSO area bounds"), so this is the procedure, not a measurement.
#   1. start with -XX:CRaCCheckpointTo; Spring takes the checkpoint itself once the context has refreshed
#   2. restore with -XX:CRaCRestoreFrom: the JVM resumes with the context started and the JIT warm
set -euo pipefail
IMAGE=${IMAGE:-menu-service-crac}
JAR=${JAR:-target/menu-service.jar}
CRAC_JDK_IMAGE=${CRAC_JDK_IMAGE:?a CRaC-enabled JDK 25 image}

docker rm -f menu-checkpoint 2>/dev/null || true
docker run --name menu-checkpoint --privileged -v "$PWD/$JAR":/app/app.jar "$CRAC_JDK_IMAGE" \
  java -XX:CRaCCheckpointTo=/app/checkpoint -Dspring.context.checkpoint=onRefresh -jar /app/app.jar || true
# Spring calls Core.checkpointRestore() after refresh; the JVM writes the image and exits (status 137 is expected)
docker commit --change 'ENTRYPOINT ["java", "-XX:CRaCRestoreFrom=/app/checkpoint"]' menu-checkpoint "$IMAGE"
docker rm menu-checkpoint
echo "run with: docker run --cap-add CHECKPOINT_RESTORE --cap-add SYS_PTRACE -p 8080:8080 $IMAGE"
# Before the checkpoint, Spring stops every Lifecycle bean (closing pools, sockets); after restore it starts them
# again. Anything else that holds a connection or a secret must implement org.crac.Resource: a checkpoint image
# contains the heap, including whatever secrets were in it.
