#Enviroment variables
variable "gcp_project" {
  description = "The GCP project id"
}

variable "gcp_pubsub_project" {
  description = "The GCP project hosting the PubSub resources"
}

variable "gcp_legacy_project" {
  description = "The legacy GCP project "
}

variable "kube_namespace" {
  description = "The Kubernetes namespace"
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

variable "service_account_bucket_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/storage/docs/access-control/iam-roles"
  default = "roles/storage.objectCreator"
}

variable "service_account_pubsub_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/pubsub/docs/access-control"
  default = "roles/pubsub.publisher"
}

variable "load_config_file" {
  description = "Do not load kube config file"
  default = false
}