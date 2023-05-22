#Environment variables


variable "gcp_pubsub_project" {
  default = "The GCP pubsub project gcp2"
}

variable "extime_service_account" {
  description = "application service account"
}

variable "labels" {
  description = "Labels used in all resources"
  type = map(string)
  default = {
    manager = "terraform"
    team = "ror"
    slack = "talk-ror"
    app = "extime"
  }
}

variable "bucket_instance_name" {
  description = "Storage Bucket name"
}

variable "pubsub_topic_name" {
  description = "PubSub Topic name"
  default = "MardukInboundQueue"
}

variable "service_account_bucket_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/storage/docs/access-control/iam-roles"
  default = "roles/storage.objectCreator"
}

variable "service_account_pubsub_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/pubsub/docs/access-control"
  default = "roles/pubsub.publisher"
}

