# Prompt Indexing API - Complete Documentation

Base URL: `/api/v1/admin/indexing`

---

## Authentication Scheme

All endpoints require Bearer JWT token in the header and `SYSTEM_ADMIN` role:

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

### 1. Index a Single Prompt

Index a specific prompt by its ID.

**Request:**

* Method: `POST`
* Path: `/prompt/{promptId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "promptId": "uuid",
  "status": "indexed", 
  "documentId": "gemini_document_id",
  "errorMessage": null
}
```
*Note: `status` can be `indexed`, `pending`, `failed`, or `skipped`.*

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 2. Reindex a Prompt

Delete the existing index for a prompt and recreate it.

**Request:**

* Method: `POST`
* Path: `/prompt/{promptId}/reindex`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "promptId": "uuid",
  "status": "indexed",
  "documentId": "new_gemini_document_id",
  "errorMessage": null
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 3. Batch Index All Pending Prompts

Trigger indexing for all prompts that are pending indexing.

**Request:**

* Method: `POST`
* Path: `/batch`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
[
  {
    "promptId": "uuid1",
    "status": "indexed",
    "documentId": "doc_id_1",
    "errorMessage": null
  },
  {
    "promptId": "uuid2",
    "status": "failed",
    "documentId": null,
    "errorMessage": "Error details"
  }
]
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 4. Remove Prompt from Index

Remove a prompt from the search index.

**Request:**

* Method: `DELETE`
* Path: `/prompt/{promptId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data: `null`

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 5. Poll Prompt Operation

Manually trigger polling for a specific prompt to check if indexing is complete.

**Request:**

* Method: `POST`
* Path: `/prompt/{promptId}/poll`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data: `"Operation completed and prompt indexed successfully"` 
*OR*
* Data: `"Operation still processing, check again later"`

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 6. Poll All Pending Operations

Manually trigger polling for all pending indexing operations.

**Request:**

* Method: `POST`
* Path: `/poll-all`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data: `"Polling completed. Check logs for results."`

**Roles Allowed:** `SYSTEM_ADMIN`

---

## Error Handling Guide

**HTTP Status Codes:**

* `200`: Success — process `data`
* `400`: Validation error — show `error.messages`
* `401`: Unauthorized — invalid or missing token
* `403`: Forbidden — role not permitted
* `404`: Resource not found (invalid ID)
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `November 2025`
