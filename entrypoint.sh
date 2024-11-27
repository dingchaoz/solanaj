#!/bin/bash

# Generate new keypair if it doesn't exist
if [ ! -f /root/.config/solana/id.json ]; then
    solana-keygen new --no-bip39-passphrase -o /root/.config/solana/id.json
fi

# Start the validator
exec solana-test-validator \
    --no-bpf-jit \
    --reset \
    --rpc-port 8899 \
    --faucet-port 9900 \
    --bind-address 0.0.0.0 \
    --no-snapshot-fetch \
    --log - \
    --ledger /root/.config/solana/ledger \
    --limit-ledger-size 50000000 \
    --quiet