# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}

# create service account
resource "google_service_account" "extime_service_account" {
  account_id   = "ror-extime-sa"
  display_name = "ror-extime-sa service account"
  project = var.gcp_project
}

# add service account as member to the bucket
resource "google_storage_bucket_iam_member" "storage_bucket_iam_member" {
  bucket = var.bucket_instance_name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.extime_service_account.email}"
}

# add service account as member to pubsub service in the resources project
resource "google_project_iam_member" "pubsub_project_iam_member" {
  project = var.gcp_pubsub_project
  role    = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to pubsub service in the workload project
# TODO to be removed after cluster migration
resource "google_project_iam_member" "pubsub_iam_member" {
  project = var.gcp_project
  role    = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# create key for service account
resource "google_service_account_key" "extime_service_account_key" {
  service_account_id = google_service_account.extime_service_account.name
}

  # Add SA key to to k8s
resource "kubernetes_secret" "extime_service_account_credentials" {
  metadata {
    name      = "ror-extime-sa"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = "${base64decode(google_service_account_key.extime_service_account_key.private_key)}"
  }
}