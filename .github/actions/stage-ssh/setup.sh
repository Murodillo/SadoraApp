#!/usr/bin/env bash
# Writes ~/.ssh/config for `ssh stage` and puts the `stage` helper on PATH.
set -euo pipefail

for value in "$JUMP" "$HOST"; do
  [[ $value =~ ^[a-z_][a-z0-9_-]*@[A-Za-z0-9.-]+$ ]] || { echo "::error::STAGE_JUMP and STAGE_HOST must look like user@host"; exit 1; }
  # The repository is public and so are its logs: the addresses never appear in them.
  echo "::add-mask::${value#*@}"
done

install -d -m 700 ~/.ssh
printf '%s\n' "$KEY" > ~/.ssh/stage_ci
chmod 600 ~/.ssh/stage_ci
ssh-keygen -y -f ~/.ssh/stage_ci > /dev/null 2>&1 || { echo "::error::STAGE_SSH_KEY is not a usable private key"; exit 1; }
printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/stage_known_hosts

# StrictHostKeyChecking=yes with keys pinned in a secret: a runner never learns a host
# key on first use, so a machine answering in the server's place is refused, not trusted.
common="  IdentityFile ~/.ssh/stage_ci
  IdentitiesOnly yes
  BatchMode yes
  StrictHostKeyChecking yes
  UserKnownHostsFile ~/.ssh/stage_known_hosts
  ConnectTimeout 25
  ServerAliveInterval 20
  ServerAliveCountMax 6
  LogLevel ERROR"
cat > ~/.ssh/config <<CONFIG
Host stage-jump
  HostName ${JUMP#*@}
  User ${JUMP%@*}
$common

Host stage
  HostName ${HOST#*@}
  User ${HOST%@*}
  ProxyJump stage-jump
$common
CONFIG
chmod 600 ~/.ssh/config

install -d ~/.local/bin
install -m 755 "$GITHUB_ACTION_PATH/stage" ~/.local/bin/stage
echo "$HOME/.local/bin" >> "$GITHUB_PATH"
