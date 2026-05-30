FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io docker-compose-plugin git curl ca-certificates openssh-client \
    && rm -rf /var/lib/apt/lists/*

USER jenkins
RUN jenkins-plugin-cli --plugins \
    git \
    workflow-aggregator \
    pipeline-stage-view \
    credentials \
    credentials-binding \
    ssh-agent \
    docker-workflow \
    timestamper \
    ws-cleanup
