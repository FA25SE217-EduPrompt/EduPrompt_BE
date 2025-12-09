# Semantic Search API - Complete Documentation

Base URL: `/api/v1/search`

---

## Authentication Scheme

All endpoints require Bearer JWT token in the header and one of the following roles: `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`.

```
Authorization: Bearer <token>
```

---

## Response Envelope

All responses follow this structure:

```json
{
  "data": { },
  "error": {
    "code": "ERROR_CODE",
    "messages": ["error message 1", "error message 2"],
    "status": "HTTP_STATUS"
  }
}
```

**Rules:**

* Success (2xx): `data` populated, `error` is `null`
* Error (4xx/5xx): `data` is `null`, `error` populated

---

## Endpoints

### 1. Perform Semantic Search

Search for prompts using natural language query.

**Request:**

* Method: `POST`
* Path: `/`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "query": "How to write an essay about AI", //required
  "limit": 10, // max 20 prompts
  "context": { //optional
    "tags": ["Education", "Technology"],
    "currentPrompt": "Optional context from current prompt",
    "visibility": "PUBLIC",
    "schoolId": "uuid",
    "groupId": "uuid"
  },
  "username": "optional_username" //optional
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "results": [
    {
      "promptId": "uuid",
      "title": "AI Essay Prompt",
      "description": "A prompt for writing about AI.",
      "relevanceScore": 0.85,
      "matchedSnippet": "...discuss the impact of AI on society...",
      "reasoning": "Matches query about AI essay.",
      "visibility": "PUBLIC",
      "createdBy": "user_uuid",
      "createdByName": "John Doe",
      "averageRating": 4.5 
    }
  ],
  "totalFound": 1,
  "searchId": "search_session_id",
  "executionTimeMs": 150
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

## Error Handling Guide

**HTTP Status Codes:**

* `200`: Success — process `data`
* `400`: Validation error — show `error.messages`
* `401`: Unauthorized — invalid or missing token
* `403`: Forbidden — role not permitted
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `November 2025`
