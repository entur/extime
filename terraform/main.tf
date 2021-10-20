# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}

provider "google" {
  version = "~> 3.89.0"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
  version = "~> 1.13.4"
}

# create service account
resource "google_service_account" "extime_service_account" {
  account_id = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_resources_project
}

# add service account as member to the bucket
resource "google_storage_bucket_iam_member" "storage_bucket_iam_member" {
  bucket = var.bucket_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.extime_service_account.email}"
}

# add service account as member to pubsub service in the resources project
resource "google_pubsub_topic_iam_member" "pubsub_topic_iam_member" {
  project = var.gcp_resources_project
  topic = var.pubsub_topic_name
  role = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.extime_service_account.email}"
}

# create key for service account
resource "google_service_account_key" "extime_service_account_key" {
  service_account_id = google_service_account.extime_service_account.name
}

# Add SA key to to k8s
resource "kubernetes_secret" "extime_service_account_credentials" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = base64decode(google_service_account_key.extime_service_account_key.private_key)
  }
}