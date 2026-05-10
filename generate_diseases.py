import json

labels = [
    "Apple___Apple_scab", "Apple___Black_rot", "Apple___Cedar_apple_rust", "Apple___healthy",
    "Blueberry___healthy", "Cherry_(including_sour)___Powdery_mildew", "Cherry_(including_sour)___healthy",
    "Corn_(maize)___Cercospora_leaf_spot_Gray_leaf_spot", "Corn_(maize)___Common_rust",
    "Corn_(maize)___Northern_Leaf_Blight", "Corn_(maize)___healthy", "Grape___Black_rot",
    "Grape___Esca_(Black_Measles)", "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)", "Grape___healthy",
    "Orange___Haunglongbing_(Citrus_greening)", "Peach___Bacterial_spot", "Peach___healthy",
    "Pepper,_bell___Bacterial_spot", "Pepper,_bell___healthy", "Potato___Early_blight",
    "Potato___Late_blight", "Potato___healthy", "Raspberry___healthy", "Soybean___healthy",
    "Squash___Powdery_mildew", "Strawberry___Leaf_scorch", "Strawberry___healthy",
    "Tomato___Bacterial_spot", "Tomato___Early_blight", "Tomato___Late_blight", "Tomato___Leaf_Mold",
    "Tomato___Septoria_leaf_spot", "Tomato___Spider_mites_Two-spotted_spider_mite", "Tomato___Target_Spot",
    "Tomato___Tomato_Yellow_Leaf_Curl_Virus", "Tomato___Tomato_mosaic_virus", "Tomato___healthy"
]

diseases = []
for label in labels:
    parts = label.split("___")
    crop = parts[0].replace("_", " ")
    disease_name = parts[1].replace("_", " ") if len(parts) > 1 else label
    is_healthy = "healthy" in label.lower()
    
    disease = {
        "id": label,
        "name": "Healthy" if is_healthy else disease_name,
        "nameHindi": "",
        "affectedCrop": crop,
        "affectedCropHindi": "",
        "causeType": "None" if is_healthy else "Fungal", # Defaulting to fungal for script simplicity, real app would have specific data
        "weatherTrigger": "None" if is_healthy else "High humidity and warm temperatures",
        "about": "This plant appears perfectly healthy." if is_healthy else f"{disease_name} is a common disease affecting {crop}.",
        "symptoms": ["No symptoms"] if is_healthy else [f"Spots on leaves", "Wilting or yellowing"],
        "preventionOrganic": ["Maintain good soil health"] if is_healthy else ["Prune affected areas", "Apply neem oil"],
        "preventionChemical": ["None needed"] if is_healthy else ["Apply appropriate fungicide/bactericide"],
        "dosageSafety": "N/A" if is_healthy else "Always wear protective gear when applying chemicals.",
        "severityDefault": "Low" if is_healthy else "Medium",
        "yieldLoss": "0%" if is_healthy else "10-50%",
        "marketImpact": "High market value" if is_healthy else "Reduced marketability due to blemishes.",
        "recoveryWeeks": 0 if is_healthy else 3
    }
    
    # Add a bit of specific data for a few common ones to make the UI look good
    if label == "Potato___Late_blight":
        disease["causeType"] = "Oomycete (Water Mold)"
        disease["severityDefault"] = "High"
        disease["yieldLoss"] = "Up to 100%"
        disease["about"] = "Late blight is a devastating disease that can destroy entire fields within days."
    elif label == "Tomato___Tomato_Yellow_Leaf_Curl_Virus":
        disease["causeType"] = "Viral"
        disease["severityDefault"] = "High"
        disease["yieldLoss"] = "Up to 100%"
    elif label == "Tomato___Bacterial_spot":
        disease["causeType"] = "Bacterial"
    
    diseases.append(disease)

with open("c:/Users/kings/OneDrive/Desktop/Documents/Doctor__Plant/PlantCureApp/app/src/main/assets/diseases.json", "w") as f:
    json.dump(diseases, f, indent=2)

print("Created diseases.json successfully.")
