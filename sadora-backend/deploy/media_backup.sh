#!/bin/sh
#
# Nightly backup of message photos and videos (media/messages), with restic: incremental,
# so a night copies only what is new, and encrypted again on top of the files' own seal.
# tools/deploy_prod.sh installs it in /etc/cron.d once server/media-backup.env exists:
#
#   # Where the copies go: another machine or disk in Uzbekistan — never the staging
#   # server, which must not hold production data. Any restic repository works, e.g.
#   #   sftp:backup@10.10.10.20:/srv/restic/sadora-media   or   /mnt/backup/sadora-media
#   RESTIC_REPOSITORY=
#   # The repository's password, in a file only root reads. Keep a copy in the password
#   # manager with MEDIA_MASTER_KEY: a backup needs both to be of any use.
#   RESTIC_PASSWORD_FILE=/opt/sadora-prod/server/secrets/restic-password
#
# The server needs restic itself: `apt install restic`. A good run writes the time to
# media/.last-backup, which the admin panel's system page shows; a run that fails leaves
# the old time there, and the page turns it amber after two days.
#
set -eu

DIR=$(cd "$(dirname "$0")/.." && pwd)
CONF="$DIR/server/media-backup.env"
MEDIA="$DIR/media/messages"

[ -f "$CONF" ] || { echo "media backup: $CONF is missing"; exit 1; }
command -v restic >/dev/null 2>&1 || { echo "media backup: restic is not installed (apt install restic)"; exit 1; }
# shellcheck disable=SC1090
. "$CONF"
export RESTIC_REPOSITORY RESTIC_PASSWORD_FILE
mkdir -p "$MEDIA"

# The first run makes the repository.
restic cat config >/dev/null 2>&1 || restic init

# Unfinished uploads and half-written files are not worth keeping.
restic backup --tag sadora-media --exclude '*.part' --exclude '*.next' "$MEDIA"
restic forget --tag sadora-media --keep-daily 14 --keep-weekly 8 --keep-monthly 12 --prune

date -u +%Y-%m-%dT%H:%M:%SZ > "$DIR/media/.last-backup"
echo "media backup: done $(cat "$DIR/media/.last-backup")"
