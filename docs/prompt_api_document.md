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

* Body: `CreatePromptRequest`

**Response:**

* Status: `201`
* Data: `DetailPromptResponse`

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

* Body: `CreatePromptCollectionRequest`

**Response:**

* Status: `201`
* Data: `DetailPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 3. Get My Prompts

Retrieve prompts created by the current authenticated user.

**Request:**

* Method: `GET`
* Path: `/my-prompt`
* Query Parameters: `page`, `size`

**Response:**

* Status: `200`
* Data: `PaginatedDetailPromptResponse`

**Roles Allowed:** `TEACHER`, `SYSTEM_ADMIN`

---

### 4. Get Prompts by User ID

Retrieve all prompts created by a specific user.

**Request:**

* Method: `GET`
* Path: `/user/{userId}`
* Query Parameters: `page`, `size`

**Response:**

* Status: `200`
* Data: `PaginatedPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 5. Get Non-Private Prompts

Retrieve all prompts that visibility are not PRIVATE.

**Request:**

* Method: `GET`
* Path: `/get-non-private`
* Query Parameters: `page`, `size`

**Response:**

* Status: `200`
* Data: `PaginatedPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 6. Get Prompts by Collection ID

Retrieve all prompts belonging to a specific collection.

**Request:**

* Method: `GET`
* Path: `/collection/{collectionId}`
* Query Parameters: `page`, `size`

**Response:**

* Status: `200`
* Data: `PaginatedPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 7. Update Prompt Metadata

Update a prompt’s title, content, description and some more field. Note that title must not null

**Request:**

* Method: `PUT`
* Path: `/{promptId}/metadata`
* Body: `UpdatePromptMetadataRequest`

**Response:**

* Status: `200`
* Data: `DetailPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 8. Update Prompt Visibility

Change the visibility of a prompt (e.g., PRIVATE → PUBLIC or GROUP).

**Request:**

* Method: `PUT`
* Path: `/{promptId}/visibility`
* Body: `UpdatePromptVisibilityRequest`

**Response:**

* Status: `200`
* Data: `DetailPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 9. Soft Delete Prompt

Soft-delete a prompt (mark as inactive without permanent removal).

**Request:**

* Method: `DELETE`
* Path: `/{id}`

**Response:**

* Status: `200`
* Data: Message

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 10. Filter Prompts

Filter prompts by various parameters (creator, tags, collection, school, etc.).

**Request:**

* Method: `GET`
* Path: `/filter`
* Query Parameters: `createdBy`, `collectionName`, `tagTypes`, `tagValues`, `schoolName`, `groupName`, `title`, `includeDeleted`, `page`, `size`

**Response:**

* Status: `200`
* Data: `PaginatedPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 11. Get Prompt by ID

Retrieve details of a specific prompt.

**Request:**

* Method: `GET`
* Path: `/{promptId}`

**Response:**

* Status: `200`
* Data: `DetailPromptResponse`

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 12. Unlock / Log Prompt View
Marks a prompt as "viewed" (unlocked) by the current user. This is required to track usage or unlock content limits.

**POST** `/prompt-view-log/new`

**Request Body:**
```json
{
  "promptId": "UUID" // Required
}
```

**Response:** `PromptViewLogResponse`

---

### 13. Check Unlock Status
Checks if the current user has already unlocked (viewed) a specific prompt.

**GET** `/{promptId}/viewed`

**Response:** `true` if unlocked, `false` otherwise.

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

**Response:** `PromptVersionResponse`
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

**Response:** List of `PromptVersionResponse`

---

### 16. Rollback Prompt Version
Restores a prompt to a previous version. This updates the prompt's current content to match the selected version and creates a new history entry (conceptually, though the implementation might just update the current state and link to the version).

**PUT** `/{promptId}/rollback/{versionId}`

**Response:** `DetailPromptResponse` (The updated prompt details)

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

API Version: `1.1`
Last Updated: `November 2025`
