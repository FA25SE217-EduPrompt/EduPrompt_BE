# Gemini Admin API - Complete Documentation

Base URL: `/api/v1/admin/gemini`

**Note:** For store IDs and document IDs that contain slashes (e.g., `fileSearchStores/123`), you may need to URL-encode them or use underscores `_` as delimiters if the server supports it (as mentioned in the controller description). The controller normalizes `_` to `/`.

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

### 1. Create File Search Store

Create a new store for indexing documents.

**Request:**

* Method: `POST`
* Path: `/stores`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:
    * `displayName`: Name of the store (required)

**Response:**

* Status: `201`
* Data:

```json
{
  "storeId": "fileSearchStores/uuid",
  "displayName": "Store Name",
  "createdAt": "2025-01-20T10:00:00Z",
  "updatedAt": "2025-01-20T10:00:00Z",
  "activeDocumentCount": 0
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 2. Get Store Details

Get metadata for a file search store.

**Request:**

* Method: `GET`
* Path: `/stores/{storeId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "storeId": "fileSearchStores/uuid",
  "displayName": "Store Name",
  "createdAt": "2025-01-20T10:00:00Z",
  "updatedAt": "2025-01-20T10:00:00Z",
  "activeDocumentCount": 5
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 3. Delete Store

Delete a file search store and all its documents.

**Request:**

* Method: `DELETE`
* Path: `/stores/{storeId}`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:
    * `force`: `true` or `false` (default `false`)

**Response:**

* Status: `204`
* Data: `null`

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 4. List Documents

List all documents in a store.

**Request:**

* Method: `GET`
* Path: `/stores/{storeId}/documents`
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
    "name": "documents/uuid",
    "displayName": "filename.pdf",
    "state": "ACTIVE",
    "sizeBytes": 1024,
    "mimeType": "application/pdf",
    "createTime": "2025-01-20T10:00:00Z",
    "updateTime": "2025-01-20T10:00:00Z"
  }
]
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 5. Get Document

Get document metadata.

**Request:**

* Method: `GET`
* Path: `/documents/{documentId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "name": "documents/uuid",
  "displayName": "filename.pdf",
  "state": "ACTIVE",
  "sizeBytes": 1024,
  "mimeType": "application/pdf",
  "createTime": "2025-01-20T10:00:00Z",
  "updateTime": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 6. Delete Document

Delete a specific document.

**Request:**

* Method: `DELETE`
* Path: `/documents/{documentId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `204`
* Data: `null`

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 7. Poll Operation

Check status of a long-running operation (e.g., import).

**Request:**

* Method: `GET`
* Path: `/operations/{operationName}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "operationName": "operations/uuid",
  "done": true,
  "status": "completed",
  "documentId": "documents/uuid",
  "errorMessage": null
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 8. Test Search

Debug endpoint to test raw search against Gemini.

**Request:**

* Method: `POST`
* Path: `/stores/{storeId}/search`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "query": "search term",
  "limit": "10"
}
```

**Response:**

* Status: `200`
* Data:

```json
[
  {
    "documentId": "documents/uuid",
    "text": "Relevant text chunk...",
    "confidenceScore": 0.95,
    "startIndex": 100,
    "endIndex": 200
  }
]
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

## Error Handling Guide

**HTTP Status Codes:**

* `200`: Success — process `data`
* `201`: Created
* `204`: No Content
* `400`: Validation error — show `error.messages`
* `401`: Unauthorized — invalid or missing token
* `403`: Forbidden — role not permitted
* `404`: Resource not found (invalid ID)
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `November 2025`
