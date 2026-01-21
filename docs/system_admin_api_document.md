```markdown
# System Admin - Payments & Token Usage API Documentation

**Base URL:** `/api/v1/admin`

**Authentication:**  
All endpoints require Bearer JWT with role `SYSTEM_ADMIN`.

```
Authorization: Bearer <jwt-token>
```

**Response Envelope** (same as other APIs):

```json
{
  "data": { ... } | null,
  "error": {
    "code": "ERROR_CODE",
    "messages": ["Human readable message"],
    "status": "HTTP_STATUS"
  } | null
}
```

---

## Relevant Endpoints

### 1. Get Monthly Payment Summary (Admin View)

**Aggregated revenue & transaction stats per month**

- **Method:** `GET`
- **Path:** `/payments-summary-monthly`

**Response – 200 OK**

```json
{
  "data": [
    {
      "year": 2025,
      "month": 12,
      "monthName": "December",
      "totalAmount": 148750000,
      "totalTransactions": 187,
      "successfulCount": 174,
      "pendingCount": 8,
      "failedCount": 5,
      "averageAmount": 795455.08
    },
    {
      "year": 2026,
      "month": 1,
      "monthName": "January",
      "totalAmount": 92750000,
      "totalTransactions": 124,
      "successfulCount": 119,
      "pendingCount": 3,
      "failedCount": 2,
      "averageAmount": 748790.32
    }
  ],
  "error": null
}
```

**Fields Explanation:**

| Field              | Type    | Description                              |
|--------------------|---------|------------------------------------------|
| year               | Integer | e.g. 2026                                |
| month              | Integer | 1–12                                     |
| monthName          | String  | "January", "February", …                 |
| totalAmount        | Long    | Total money collected (smallest unit)    |
| totalTransactions  | Long    | All attempts                             |
| successfulCount    | Long    | Completed & paid                         |
| pendingCount       | Long    | Waiting for confirmation                 |
| failedCount        | Long    | Failed / cancelled                       |
| averageAmount      | Double  | `totalAmount / successfulCount`          |

---

### 2. List All Payments – Paginated (Admin View)

**Full list of payment records with filtering**

- **Method:** `GET`
- **Path:** `/all-payments`
- **Query Parameters:**
    - `page`          (default: 0)
    - `size`          (default: 20)
    - `status`        (optional) – e.g. `SUCCESS`, `PENDING`, `FAILED`
    - `yearMonth`     (optional) – format: `2025-12` or `2026-01`

**Response – 200 OK**

```json
{
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "email": "nguyen.van.a@school.edu.vn",
        "fullName": "Nguyễn Văn A",
        "tierId": "e7b8f9c0-12ab-4def-89ab-cdef01234567",
        "tierName": "Premium Yearly",
        "amount": 2500000,
        "orderInfo": "Gói Premium 1 năm - trường Green School",
        "status": "SUCCESS",
        "createdAt": "2026-01-19T14:22:10.147Z",
        "paidAt": "2026-01-19T14:25:33.891Z"
      }
    ],
    "totalElements": 1247,
    "totalPages": 63,
    "pageNumber": 0,
    "pageSize": 20
  },
  "error": null
}
```

---

### 3. View Current Token Status for School Subscriptions

**Remaining tokens & quota info for school plans**

- **Method:** `GET`
- **Path:** `/school-subscriptions-tokens`
- **Query Parameters:**
    - `activeOnly` (boolean, optional, default: `true`)

**Response – 200 OK**

```json
{
  "data": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "schoolId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "schoolName": "Trường Quốc tế Green",
      "schoolTokenPool": 1500000,
      "schoolTokenRemaining": 942180,
      "tokensUsed": 557820,
      "quotaResetDate": "2026-02-01T00:00:00Z",
      "isActive": true,
      "endDate": "2026-08-31T23:59:59Z"
    }
  ],
  "error": null
}
```

---

### 4. List All Teacher Token Usage Logs (Paginated)

**Detailed consumption history – all teachers**

- **Method:** `GET`
- **Path:** `/teacher-token-usage`
- **Query:** `page`, `size` (default 0 / 20)

**Response Structure:**

```json
{
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "schoolSubscriptionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "subscriptionTierId": "e7b8f9c0-12ab-4def-89ab-cdef01234567",
        "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "tokensUsed": 420,
        "usedAt": "2026-01-19T14:35:22.147Z"
      }
    ],
    "totalElements": 5284,
    "totalPages": 265,
    "pageNumber": 0,
    "pageSize": 20
  },
  "error": null
}
```

---

### 5. List Token Usage for One Specific School Subscription

**Filter logs to one school subscription**

- **Method:** `GET`
- **Path:** `/school-subscriptions/{subscriptionId}/token-usage`
- **Path Variable:** `subscriptionId` (UUID)
- **Query:** `page`, `size`

**Response:** Same paginated structure as above, but filtered.

---

### 6. Monthly Aggregated Teacher Token Usage Summary (Platform-wide)

**High-level monthly usage stats**

- **Method:** `GET`
- **Path:** `/teacher-token/usage-monthly`

**Response – 200 OK**

```json
{
  "data": [
    {
      "year": 2025,
      "month": 12,
      "monthName": "December",
      "totalTokensUsed": 4281900,
      "usageCount": 15420,
      "uniqueTeachers": 187
    },
    {
      "year": 2026,
      "month": 1,
      "monthName": "January",
      "totalTokensUsed": 1543200,
      "usageCount": 6730,
      "uniqueTeachers": 203
    }
  ],
  "error": null
}
```

---

## Quick Reference – Most Useful Admin Dashboard Calls

| Purpose                              | Endpoint                                    | Recommended Params                  |
|--------------------------------------|---------------------------------------------|--------------------------------------|
| Monthly revenue overview             | `/payments-summary-monthly`                 | —                                    |
| All payments (filter by status/date) | `/all-payments`                             | `status=SUCCESS`, `yearMonth=2026-01` |
| Schools running low on tokens        | `/school-subscriptions-tokens?activeOnly=true` | —                                    |
| Top consuming teachers / schools     | `/teacher-token-usage` (sort backend?)      | `page=0&size=50`                     |
| Monthly token consumption trend      | `/teacher-token/usage-monthly`              | —                                    |

**Version:** 1.1 – Payments & Token Usage APIs  
**Last Updated:** January 2026

```

You can name this file:

`system-admin-payments-token-usage-api.md`

Let me know if you want:

- to merge it with the previous token-only document
- to add example cURL / Postman snippets
- to include estimated error codes for each endpoint
- to document the missing inner `TeacherTokenUsageLogResponse` structure (if you can provide that DTO)

Happy to refine further!