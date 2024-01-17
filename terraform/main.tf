# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}

provider "google" {
  version = "~> 4.84.0"
}

# add service account as member to the bucket
resource "google_storage_bucket_iam_member" "storage_bucket_iam_member" {
  bucket = var.bucket_instance_name
  role = var.service_account_bucket_role
  member = var.extime_service_account
}

# add service account as member to pubsub service in the gcp2 project

resource "google_pubsub_topic_iam_member" "pubsub_topic_iam_member" {
  project = var.gcp_pubsub_project
  topic = var.pubsub_topic_name
  role = var.service_account_pubsub_role
  member = var.extime_service_account
}
