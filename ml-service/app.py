import json
from typing import Any, Dict, List

from flask import Flask, jsonify, request
from flask_cors import CORS

from services import analyser, explainer, severity_scorer

app = Flask(__name__)
CORS(
    app,
    resources={r"/api/*": {"origins": ["http://localhost:8080", "http://127.0.0.1:8080"]}},
)


@app.get("/api/ml/health")
def health() -> Any:
    return jsonify({"status": "ok", "models_loaded": analyser.models_loaded()})


@app.post("/api/ml/analyse")
def analyse_endpoint() -> Any:
    if not analyser.models_loaded():
        return jsonify({"error": "Models not loaded"}), 500

    try:
        payload = request.get_json(force=True, silent=False)
    except Exception as ex:
        return jsonify({"error": f"Invalid JSON payload: {ex}"}), 400

    if not isinstance(payload, list):
        return jsonify({"error": "Expected a JSON array of traffic records"}), 400

    records: List[Dict[str, Any]] = payload

    try:
        analysis_results = analyser.analyse(records)
    except Exception as ex:
        return jsonify({"error": f"Analysis failed: {ex}"}), 500

    response: List[Dict[str, Any]] = []

    for rec, res in zip(records, analysis_results):
        threat_type = res["threat_type"]
        confidence = res.get("confidence_score", 0.0)

        severity, adjusted_conf = severity_scorer.score(threat_type, confidence)
        explanation = explainer.explain(rec, threat_type)

        response.append(
            {
                "threat_type": threat_type,
                "severity": severity,
                "confidence_score": adjusted_conf,
                "explanation": explanation,
            }
        )

    return app.response_class(
        response=json.dumps(response),
        status=200,
        mimetype="application/json",
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)

