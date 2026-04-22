# ThreatSense5G ML Microservice

This folder contains a standalone Python Flask microservice that provides
machine learning analysis for 5G network traffic.

It exposes a simple HTTP API for the main Spring Boot application (or other
clients) to send traffic records and receive threat classifications,
severity scores, and short human-readable explanations.

## 1. Setup

From the `ml-service` directory:

```bash
cd ml-service
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install --upgrade pip
pip install -r requirements.txt
```

## 2. Train models

Run the training script once to generate and persist the ML models:

```bash
python models/train_models.py
```

This will:

- Generate synthetic 5G-like traffic (10,000 rows) with labels:
  `NORMAL`, `DDOS`, `INTRUSION`
- Train:
  - An Isolation Forest anomaly detector (`isolation_forest.pkl`)
  - A Random Forest classifier (`random_forest.pkl`)
- Print accuracy, precision, recall, and F1-score for the classifier.

The model artifacts are saved into `ml-service/models/`.

## 3. Run the microservice

From the `ml-service` directory:

```bash
python app.py
```

The Flask app will start on **port 5001**:

- `GET /api/ml/health` – health check.
- `POST /api/ml/analyse` – analyse traffic records.

Cross-origin requests from `http://localhost:8080` are allowed.

## 4. API

### Health check

```bash
curl http://localhost:5001/api/ml/health
```

Example response:

```json
{
  "status": "ok",
  "models_loaded": true
}
```

### Analyse traffic records

`POST /api/ml/analyse` expects a JSON array of traffic objects with at least:

- `packet_size` (number)
- `packet_count` (number)
- `duration_ms` (number)
- `protocol` (string: `TCP`, `UDP`, `ICMP`, other)
- `slice_type` (string: `eMBB`, `URLLC`, `mMTC`)

Example request:

```bash
curl -X POST http://localhost:5001/api/ml/analyse \
  -H "Content-Type: application/json" \
  -d '[
    {
      "packet_size": 1200,
      "packet_count": 450,
      "duration_ms": 30,
      "protocol": "TCP",
      "slice_type": "eMBB"
    },
    {
      "packet_size": 600,
      "packet_count": 40,
      "duration_ms": 200,
      "protocol": "UDP",
      "slice_type": "URLLC"
    }
  ]'
```

Example response:

```json
[
  {
    "threat_type": "DDOS",
    "severity": "HIGH",
    "confidence_score": 0.91,
    "explanation": "Flagged due to abnormally high packet_count (450) and short duration_ms (30), consistent with Ddos pattern."
  },
  {
    "threat_type": "NORMAL",
    "severity": "LOW",
    "confidence_score": 0.88,
    "explanation": "Traffic classified as normal based on learned baseline patterns."
  }
]
```

If the models are not loaded, the service returns HTTP 500 with a JSON error:

```json
{ "error": "Models not loaded" }
```

