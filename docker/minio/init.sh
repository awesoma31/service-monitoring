#!/bin/sh
# Prepares MinIO for report-service on every start and changes only what is missing:
# a private bucket and an account that may work with objects of that bucket alone.
# The root credentials stay here; report-service receives only the limited account.
set -eu

: "${MINIO_ROOT_USER:?}" "${MINIO_ROOT_PASSWORD:?}" "${MINIO_BUCKET:?}"
: "${MINIO_ACCESS_KEY:?}" "${MINIO_SECRET_KEY:?}"

policy=report-service-objects

until mc alias set root http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  echo "Waiting for MinIO..."
  sleep 2
done

mc mb --ignore-existing "root/$MINIO_BUCKET"
mc anonymous set none "root/$MINIO_BUCKET"

cat > /tmp/policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": ["arn:aws:s3:::${MINIO_BUCKET}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload",
                 "s3:ListMultipartUploadParts"],
      "Resource": ["arn:aws:s3:::${MINIO_BUCKET}/*"]
    }
  ]
}
EOF
mc admin policy create root "$policy" /tmp/policy.json

# Adding an existing user updates its secret, so a changed MINIO_SECRET_KEY takes effect.
# The mc image has no grep, and attaching an already attached policy is an error.
mc admin user add root "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
case "$(mc admin user info root "$MINIO_ACCESS_KEY")" in
  *"$policy"*) ;;
  *) mc admin policy attach root "$policy" --user "$MINIO_ACCESS_KEY" ;;
esac

echo "Bucket $MINIO_BUCKET and its account are ready"
