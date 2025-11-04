# Collection API - Complete Documentation

Base URL: `/api/collections`

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

## Endpoints

### 1. Create Collection

Create a new collection owned by the current authenticated user.

**Request:**

* Method: `POST`
* Path: `/create`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "name": "My Collection",
  "description": "Collection for math prompts",
  "visibility": "PRIVATE"
}
```

**Response:**

* Status: `201`
* Data:

```json
{
  "id": "uuid",
  "name": "My Collection",
  "ownerId": "uuid",
  "visibility": "PRIVATE",
  "createdAt": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

**Errors:**

| Code            | Status | Cause                      |
| --------------- | ------ | -------------------------- |
| `INVALID_INPUT` | 400    | Invalid or missing fields  |
| `UNAUTHORIZED`  | 401    | Missing/invalid token      |
| `FORBIDDEN`     | 403    | Role not allowed to create |

---

### 2. Get My Collections

Retrieve all collections created by the authenticated user.

**Request:**

* Method: `GET`
* Path: `/my-collection`
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
    {
      "id": "uuid",
      "name": "Collection A",
      "visibility": "PRIVATE"
    },
    {
      "id": "uuid",
      "name": "Collection B",
      "visibility": "PUBLIC"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 3. Get Public Collections

Retrieve all collections with `PUBLIC` visibility.

**Request:**

* Method: `GET`
* Path: `/public`
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
    {
      "id": "uuid",
      "name": "Public Collection 1",
      "ownerName": "Tri Nguyen",
      "visibility": "PUBLIC"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 4. Get All Collections (Admin)

Retrieve all collections for admin users (school/system).

**Request:**

* Method: `GET`
* Path: `/all`
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
    {
      "id": "uuid",
      "name": "Teacher Collection",
      "ownerName": "John Doe",
      "visibility": "GROUP"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

**Roles Allowed:** `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 5. Get All Collections (System Admin - Including Deleted)

Retrieve all collections, including soft-deleted ones. Only accessible by `SYSTEM_ADMIN`.

**Request:**

* Method: `GET`
* Path: `/all/admin`
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
    {
      "id": "uuid",
      "name": "Old Deleted Collection",
      "deleted": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

**Roles Allowed:** `SYSTEM_ADMIN`

---

### 6. Update Collection

Update the collection details (name, description, visibility).

**Request:**

* Method: `PUT`
* Path: `/{id}`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "name": "Updated Collection",
  "description": "New details",
  "visibility": "GROUP"
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "name": "Updated Collection",
  "visibility": "GROUP",
  "updatedAt": "2025-01-20T12:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

**Errors:**

| Code                   | Status | Cause                              |
| ---------------------- | ------ | ---------------------------------- |
| `COLLECTION_NOT_FOUND` | 404    | Invalid ID                         |
| `FORBIDDEN`            | 403    | User not owner or lacks permission |
| `INVALID_INPUT`        | 400    | Invalid field values               |

---

### 7. Soft Delete Collection

Mark a collection as deleted without permanently removing it.

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
  "message": "Collection deleted successfully"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

**Errors:**

| Code                   | Status | Cause                         |
| ---------------------- | ------ | ----------------------------- |
| `COLLECTION_NOT_FOUND` | 404    | Invalid or missing collection |
| `FORBIDDEN`            | 403    | User not allowed to delete    |

---

## Error Handling Guide

**HTTP Status Codes:**

* `200`: Success — process `data`
* `400`: Validation error — show `error.messages`
* `401`: Unauthorized — invalid or missing token
* `403`: Forbidden — role not permitted
* `404`: Resource not found
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `October 2025`
