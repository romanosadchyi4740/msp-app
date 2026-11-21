locals {
  # The one origin of the app (Caddy serves the frontend, API and WebSocket on it); used by the bucket CORS and outputs
  app_origin = "https://${var.app_domain}"
}
