from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
from app.services.ai_service import GeminiService

router = APIRouter()

class SummarizeRequest(BaseModel):
    text: str

class DescribeImageRequest(BaseModel):
    image_base64: str
    mime_type: Optional[str] = "image/jpeg"

class RefineSpeechRequest(BaseModel):
    raw_text: str

class AiTextResponse(BaseModel):
    result: str
    status: str = "success"

@router.post("/summarize", response_model=AiTextResponse)
def summarize_message(req: SummarizeRequest):
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="El texto a resumir no puede estar vacío.")
    
    summary = GeminiService.summarize_text(req.text)
    return AiTextResponse(result=summary)

@router.post("/describe-image", response_model=AiTextResponse)
def describe_image(req: DescribeImageRequest):
    if not req.image_base64.strip():
        raise HTTPException(status_code=400, detail="La imagen en Base64 es requerida.")
    
    description = GeminiService.describe_image(req.image_base64, req.mime_type or "image/jpeg")
    return AiTextResponse(result=description)

@router.post("/refine-speech", response_model=AiTextResponse)
def refine_speech(req: RefineSpeechRequest):
    if not req.raw_text.strip():
        raise HTTPException(status_code=400, detail="El texto dictado no puede estar vacío.")
    
    refined = GeminiService.refine_speech_text(req.raw_text)
    return AiTextResponse(result=refined)
