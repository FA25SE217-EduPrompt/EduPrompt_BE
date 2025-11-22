# Prompt API - Complete Documentation

Base URL: `/api/prompts`

---

## Authentication Scheme

All endpoints require Bearer JWT token in the header:

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

<<<<<<< Updated upstream
=======
## Data Structures

### PromptResponse (Metadata)
Used in paginated lists.

```json
{
  "title": "string",
  "description": "string",
  "outputFormat": "string",
  "visibility": "string",
  "fullName": "string", // Creator's name
  "collectionName": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### DetailPromptResponse (Full Content)
Used for single prompt retrieval and creation responses.

```json
{
  "id": "UUID",
  "title": "string",
  "description": "string",
  "instruction": "string", // Core prompt content
  "context": "string", // Additional context
  "inputExample": "string", // Example input
  "outputFormat": "string", // Expected output format
  "constraints": "string", // Any constraints or requirements
  "visibility": "string", // PRIVATE, PUBLIC, etc.
  "fullName": "string", // Creator's name
  "collectionName": "string",
  "tags": [
    {
      "id": "UUID",
      "name": "string"
    }
  ],
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

---

>>>>>>> Stashed changes
## Endpoints

### 1. Create Standalone Prompt

Create a prompt not linked to any collection.

**Request:**

* Method: `POST`
* Path: `/standalone`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "title": "Essay on Technology",
  "content": "Discuss the pros and cons of modern AI systems.",
  "visibility": "PRIVATE"
}
```

**Response:**

* Status: `201`
* Data:

```json
{
  "id": "uuid",
  "title": "Essay on Technology",
  "visibility": "PRIVATE",
  "createdAt": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 2. Create Prompt in Collection

Create a prompt that belongs to a specific collection.

**Request:**

* Method: `POST`
* Path: `/collection`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "collectionId": "uuid",
  "title": "Group Project Task",
  "content": "Design an educational app prototype.",
  "visibility": "GROUP"
}
```

**Response:**

* Status: `201`
* Data:

```json
{
  "id": "uuid",
  "collectionId": "uuid",
  "title": "Group Project Task",
  "visibility": "GROUP",
  "createdAt": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 3. Get My Prompts

Retrieve prompts created by the current authenticated user.

**Request:**

* Method: `GET`
* Path: `/my-prompt`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:

```
page=0
size=20
```

**Response:**

* Status: `200`
* Data:

```json
{
  "content": [
    { "id": "uuid", "title": "Essay 1", "visibility": "PRIVATE" },
    { "id": "uuid", "title": "Essay 2", "visibility": "PRIVATE" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SYSTEM_ADMIN`

---

### 4. Get Prompts by User ID

Retrieve all prompts created by a specific user.

**Request:**

* Method: `GET`
* Path: `/user/{userId}`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:

```
page=0
size=20
```

**Response:**

* Status: `200`
* Data:

```json
{
  "content": [
    { "id": "uuid", "title": "Prompt 1", "visibility": "PUBLIC" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 5. Get Non-Private Prompts

Retrieve all prompts that visibility are not PRIVATE.

**Request:**

* Method: `GET`
* Path: `/get-non-private`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "content": [
    { "id": "uuid", "title": "Shared Prompt", "visibility": "PUBLIC" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 6. Get Prompts by Collection ID

Retrieve all prompts belonging to a specific collection.

**Request:**

* Method: `GET`
* Path: `/collection/{collectionId}`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:

```
page=0
size=20
```

**Response:**

* Status: `200`
* Data:

```json
{
  "content": [
    { "id": "uuid", "title": "Prompt 1", "visibility": "GROUP" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 7. Update Prompt Metadata

Update a prompt’s title, content, description and some more field. Note that title must not null

**Request:**

* Method: `PUT`
* Path: `/{promptId}/metadata`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "title": "Updated Prompt Title",
  "content": "Revised content for the essay prompt."
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "title": "Updated Prompt Title",
  "updatedAt": "2025-01-20T12:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 8. Update Prompt Visibility

Change the visibility of a prompt (e.g., PRIVATE → PUBLIC or GROUP).

**Request:**

* Method: `PUT`
* Path: `/{promptId}/visibility`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "visibility": "GROUP",
  "collectionId": "uuid (nullable)"
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "visibility": "GROUP",
  "updatedAt": "2025-01-20T12:30:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 9. Soft Delete Prompt

Soft-delete a prompt (mark as inactive without permanent removal).

**Request:**

* Method: `DELETE`
* Path: `/{id}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

* Status: `200`
* Data:

```json
{
  "message": "Prompt deleted successfully"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 10. Filter Prompts

Filter prompts by various parameters (creator, tags, collection, school, etc.).

**Request:**

* Method: `GET`
* Path: `/filter`
* Headers:

```
Authorization: Bearer <token>
```

* Query Parameters:

```
createdBy=<uuid>
collectionName=Sample Collection
tagTypes=["Topic", "Level"]
tagValues=["Math", "Advanced"]
schoolName=FPTU
groupName=GroupA
title=Essay
includeDeleted=false
page=0
size=20
```

**Response:**

* Status: `200`
* Data:

```json
{
  "content": [
    { "id": "uuid", "title": "Math Prompt", "visibility": "SCHOOL" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 11. Get Prompt by ID

Retrieve details of a specific prompt.

**Request:**

* Method: `GET`
* Path: `/{promptId}`
* Headers:

```
Authorization: Bearer <token>
```

**Response:**

<<<<<<< Updated upstream
* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "title": "Essay on Technology",
  "content": "Discuss the pros and cons of modern AI systems.",
  "visibility": "PRIVATE",
  "createdAt": "2025-01-20T10:00:00Z"
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
* `404`: Resource not found (invalid ID)
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `October 2025`
=======
---

### 14. Create Prompt Version
Creates a new version of an existing prompt. This is typically used after optimizing a prompt or manually editing it.

**POST** `/{promptId}/versions`

**Request Body:** `CreatePromptVersionRequest`
```json
{
  "instruction": "string",
  "context": "string",
  "inputExample": "string",
  "outputFormat": "string",
  "constraints": "string",
  "isAiGenerated": "boolean" // Required
}
```

**Response:**
`PromptVersionResponse`
```json
{
  "id": "UUID",
  "promptId": "UUID",
  "instruction": "string",
  "context": "string",
  "inputExample": "string",
  "outputFormat": "string",
  "constraints": "string",
  "editorId": "UUID",
  "versionNumber": "integer",
  "isAiGenerated": "boolean",
  "createdAt": "timestamp"
}
```

---

### 15. Get Prompt Versions
Retrieves all versions of a specific prompt, ordered by version number descending.

**GET** `/{promptId}/versions`

**Response:**
List of `PromptVersionResponse`
```json
[
  {
    "id": "UUID",
    "promptId": "UUID",
    "instruction": "string",
    "context": "string",
    "inputExample": "string",
    "outputFormat": "string",
    "constraints": "string",
    "editorId": "UUID",
    "versionNumber": "integer",
    "isAiGenerated": "boolean",
    "createdAt": "timestamp"
  }
]
```
>>>>>>> Stashed changes
