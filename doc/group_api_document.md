# Group API - Complete Documentation

Base URL: `/api/groups`

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

### 1. Create Group

Create a new group.

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
  "name": "Web Development Class A"
}
```

**Response:**

* Status: `201`
* Data:

```json
{
  "id": "uuid",
  "name": "Web Development Class A",
  "isActive": true,
  "createdAt": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

**Errors:**

| Code            | Status | Cause                     |
| --------------- | ------ | ------------------------- |
| `INVALID_INPUT` | 400    | Missing or invalid fields |
| `ACCESS_DENIED` | 403    | Role not permitted        |

---

### 2. Update Group

Update an existing group's name or status.

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
  "name": "Advanced Web Dev",
  "isActive": true
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "name": "Advanced Web Dev",
  "isActive": true,
  "updatedAt": "2025-01-20T12:00:00Z"
}
```

**Roles Allowed:** `SYSTEM_ADMIN`, `SCHOOL_ADMIN`, `TEACHER (if group admin)`

**Errors:**

| Code              | Status | Cause                   |
| ----------------- | ------ | ----------------------- |
| `GROUP_NOT_FOUND` | 404    | Invalid ID              |
| `ACCESS_DENIED`   | 403    | No permission to update |

---

### 3. Add Group Members

Add new members to a group.

**Request:**

* Method: `POST`
* Path: `/{id}/members`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "members": [
    { "userId": "uuid-1", "role": "MEMBER", "status": "active" },
    { "userId": "uuid-2", "role": "ADMIN" }
  ]
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "id": "uuid",
  "name": "Web Development Class A",
  "updatedAt": "2025-01-20T13:00:00Z"
}
```

**Roles Allowed:** `GROUP_ADMIN`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 4. Remove Group Member

Remove a member from the group.

**Request:**

* Method: `DELETE`
* Path: `/{id}/members`
* Headers:

```
Authorization: Bearer <token>
```

* Body:

```json
{
  "userId": "uuid-1"
}
```

**Response:**

* Status: `200`
* Data:

```json
{
  "message": "Member removed successfully"
}
```

**Roles Allowed:** `GROUP_ADMIN`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

**Errors:**

| Code                       | Status | Cause                           |
| -------------------------- | ------ | ------------------------------- |
| `MEMBER_NOT_FOUND`         | 404    | Member not in group             |
| `CANNOT_REMOVE_CREATOR`    | 400    | Attempt to remove group creator |
| `CANNOT_REMOVE_LAST_ADMIN` | 400    | Last admin removal not allowed  |

---

### 5. Get Group by ID

Retrieve details of a specific group.

**Request:**

* Method: `GET`
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
  "id": "uuid",
  "name": "Web Development Class A",
  "isActive": true,
  "createdAt": "2025-01-20T10:00:00Z"
}
```

**Roles Allowed:** `GROUP_MEMBER`, `GROUP_ADMIN`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 6. List My Groups

Retrieve groups the current user is a member or admin of.

**Request:**

* Method: `GET`
* Path: `/my-groups`
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
    { "id": "uuid-1", "name": "Group A", "isActive": true },
    { "id": "uuid-2", "name": "Group B", "isActive": true }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

**Roles Allowed:** `TEACHER`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 7. Get Group Members

List all members of a specific group.

**Request:**

* Method: `GET`
* Path: `/{id}/members`
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
    { "userId": "uuid-1", "firstName": "Tri", "lastName": "Nguyen", "role": "ADMIN", "status": "active" },
    { "userId": "uuid-2", "firstName": "Lan", "lastName": "Pham", "role": "MEMBER", "status": "active" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10,
  "totalPages": 1
}
```

**Roles Allowed:** `GROUP_MEMBER`, `GROUP_ADMIN`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

### 8. Soft Delete Group

Soft-delete a group (mark inactive without removing data).

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
  "message": "Group deleted successfully"
}
```

**Roles Allowed:** `GROUP_ADMIN`, `SCHOOL_ADMIN`, `SYSTEM_ADMIN`

---

## Error Handling Guide

**HTTP Status Codes:**

* `200`: Success — process `data`
* `400`: Validation error — show `error.messages`
* `401`: Unauthorized — invalid or missing token
* `403`: Forbidden — access denied
* `404`: Not found — invalid group or member ID
* `500`: Server error

---

## Version

API Version: `1.0`
Last Updated: `October 2025`
