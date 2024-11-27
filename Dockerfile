###############
# Build Stage #
###############
FROM --platform=linux/amd64 ubuntu:24.04 as build

# Setup Proxy
#ENV HTTPS_PROXY=http://http.proxy.fmr.com:8000/ \
#     HTTP_PROXY=http://http.proxy.fmr.com:8000/ \
#     http_proxy=http://http.proxy.fmr.com:8000/ \
#     https_proxy=http://http.proxy.fmr.com:8000/

ARG SOLANA_VERSION

# Install build dependencies
RUN apt-get update && apt-get install -y \
    curl \
    bzip2 \
    libc6

# Install Solana
WORKDIR /build
RUN curl --fail -LO "https://github.com/anza-xyz/agave/releases/download/v${SOLANA_VERSION}/solana-release-x86_64-unknown-linux-gnu.tar.bz2" \
    && tar jxf solana-release-x86_64-unknown-linux-gnu.tar.bz2

###################
# Execution Stage #
###################
FROM --platform=linux/amd64 ubuntu:24.04

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    libc6 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy Solana binaries from build stage
COPY --from=build /build/solana-release /solana-release

ENV PATH=/solana-release/bin:$PATH

# Create necessary directories
RUN mkdir -p /mnt/ledger /mnt/accounts
VOLUME ["/mnt/ledger", "/mnt/accounts"]

# DYNAMIC_PORT_RANGE=9800-9820
ARG DYNAMIC_PORT_RANGE

# FMR-Specific Labels
ARG APP_ID
ARG APP_TEAM_EMAIL
ARG BUILD_URL
ARG CREATED_BY
ARG GIT_COMMIT
ARG GIT_URL
LABEL "com.fmr.ap172729.solana.applicationid"="$APP_ID"
LABEL "com.fmr.ap172729.solana.createdby"="$CREATED_BY"
LABEL "com.fmr.ap172729.solana.pipelineurl"="$BUILD_URL"
LABEL "com.fmr.ap172729.solana.scmcommit"="$GIT_COMMIT"
LABEL "com.fmr.ap172729.solana.scmurl"="$GIT_URL"
LABEL "com.fmr.ap172729.solana.support"="$APP_TEAM_EMAIL"

ENTRYPOINT ["sh", "-c", "solana-test-validator --dynamic-port-range ${DYNAMIC_PORT_RANGE}"]