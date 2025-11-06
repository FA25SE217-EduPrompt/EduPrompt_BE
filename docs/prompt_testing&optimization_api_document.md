# EduPrompt API Documentation - Prompt Testing & Optimization

## Base URL
```
/api
```

## Authentication
All endpoints require authentication via Bearer token in the Authorization header:
```
Authorization: Bearer <your_access_token>
```

## Rate Limiting & Quotas
- **Standard**: 10 tests/day, 10 optimization/day , 20000 token
- **Pro**: 100 tests/day, 100 optimizations/day, 1000000 token
- **School-Wide**: calculated based on school token pool, which is purchased and agreed outside the system
- **Premium**: 1000 tests/day, 1000 optimizations/day, 10000000 token

---

## Prompt Testing API

### 1. Test Prompt
**POST** `/prompts/test`

Test a prompt with an AI model and get results immediately (synchronous).

#### Request Headers
```
Content-Type: application/json
Authorization: Bearer <token>
X-Idempotency-Key: <uuid> (optional, recommended for retries)
```

#### Request Body
```json
{
  "promptId": "550e8400-e29b-41d4-a716-446655440000",
  "aiModel": "GPT_4O_MINI",
  "inputText": "What is photosynthesis?",
  "temperature": 0.7,
  "maxTokens": 500,
  "topP": 1.0
}
```

**Field Descriptions:**
- `promptId` (required): UUID of the prompt to test
- `aiModel` (required): AI model to use. Options: `GPT_4O_MINI`, `CLAUDE_3_5_SONNET`, `GEMINI_2_0_FLASH`
- `inputText` (optional): Custom input text for testing. If not provided, uses prompt's input example
- `temperature` (optional): Controls randomness (0.0-2.0). Default: 0.7
- `maxTokens` (optional): Maximum tokens in response. Default: varies by model
- `topP` (optional): Nucleus sampling parameter (0.0-1.0). Default: 1.0

#### Response (200 OK)
```json
{
  "data": {
    "usageId": "660e8400-e29b-41d4-a716-446655440001",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "aiModel": "GPT_4O_MINI",
    "inputText": "What is photosynthesis?",
    "output": "Photosynthesis is the process by which...",
    "tokensUsed": 245,
    "executionTimeMs": 1842,
    "temperature": 0.7,
    "maxTokens": 500,
    "topP": 1.0,
    "createdAt": "2025-01-15T10:30:45Z"
  },
  "error": null
}
```

#### Error Response (429 Too Many Requests)
```json
{
  "data": null,
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": ["Testing quota exceeded. Resets at 2025-01-16T00:00:00Z"],
    "status": "TOO_MANY_REQUESTS"
  }
}
```

#### Error Response (409 Conflict - Concurrent Request)
```json
{
  "data": null,
  "error": {
    "code": "DUPLICATE_REQUEST",
    "message": ["Duplicate request in progress, please retry in a moment"],
    "status": "CONFLICT"
  }
}
```

#### cURL Example
```bash
curl -X POST https://api.eduprompt.com/api/prompts/test \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000" \
  -d '{
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "aiModel": "GPT_4O_MINI",
    "inputText": "Explain photosynthesis to a 10-year-old",
    "temperature": 0.7,
    "maxTokens": 300
  }'
```

---

### 2. Get Test Result by ID
**GET** `/prompts/test/usage/{usageId}`

Retrieve details of a specific test result.

#### Path Parameters
- `usageId` (required): UUID of the test usage record

#### Response (200 OK)
```json
{
  "data": {
    "usageId": "660e8400-e29b-41d4-a716-446655440001",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "aiModel": "GPT_4O_MINI",
    "inputText": "What is photosynthesis?",
    "output": "Photosynthesis is...",
    "tokensUsed": 245,
    "executionTimeMs": 1842,
    "temperature": 0.7,
    "maxTokens": 500,
    "topP": 1.0,
    "createdAt": "2025-01-15T10:30:45Z"
  },
  "error": null
}
```

---

### 3. Get Test Results by Prompt
**GET** `/prompts/test/prompt/{promptId}`

Get all test results for a specific prompt.

#### Path Parameters
- `promptId` (required): UUID of the prompt

#### Response (200 OK)
```json
{
  "data": [
    {
      "usageId": "660e8400-e29b-41d4-a716-446655440001",
      "promptId": "550e8400-e29b-41d4-a716-446655440000",
      "aiModel": "GPT_4O_MINI",
      "output": "...",
      "tokensUsed": 245,
      "createdAt": "2025-01-15T10:30:45Z"
    },
    {
      "usageId": "770e8400-e29b-41d4-a716-446655440002",
      "promptId": "550e8400-e29b-41d4-a716-446655440000",
      "aiModel": "CLAUDE_3_5_SONNET",
      "output": "...",
      "tokensUsed": 312,
      "createdAt": "2025-01-14T09:15:22Z"
    }
  ],
  "error": null
}
```

---

### 4. Get User Test History (Paginated)
**GET** `/prompts/test/history?page=0&size=20`

Get paginated test history for the authenticated user across all prompts.

#### Query Parameters
- `page` (optional): Page number, 0-indexed. Default: 0
- `size` (optional): Page size. Default: 20

#### Response (200 OK)
```json
{
  "data": {
    "content": [
      {
        "usageId": "660e8400-e29b-41d4-a716-446655440001",
        "promptId": "550e8400-e29b-41d4-a716-446655440000",
        "aiModel": "GPT_4O_MINI",
        "output": "...",
        "tokensUsed": 245,
        "createdAt": "2025-01-15T10:30:45Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20,
      "sort": {
        "sorted": true,
        "unsorted": false
      }
    },
    "totalElements": 45,
    "totalPages": 3,
    "last": false,
    "first": true,
    "numberOfElements": 20
  },
  "error": null
}
```

---

### 5. Get Prompt Test History (Paginated)
**GET** `/prompts/test/prompt/{promptId}/history?page=0&size=20`

Get paginated test history for a specific prompt by the authenticated user.

#### Path Parameters
- `promptId` (required): UUID of the prompt

#### Query Parameters
- `page` (optional): Page number, 0-indexed. Default: 0
- `size` (optional): Page size. Default: 20

#### Response
Same structure as "Get User Test History"

---

### 6. Delete Test Result
**DELETE** `/prompts/test/usage/{usageId}`

Delete a test result. Only the owner can delete their test results.

#### Path Parameters
- `usageId` (required): UUID of the test usage record

#### Response (200 OK)
```json
{
  "data": "Test result deleted successfully",
  "error": null
}
```

---

## Prompt Optimization API

### 1. Request Optimization
**POST** `/prompts/optimize`

Request prompt optimization. Returns immediately with a queue ID. Processing happens asynchronously in the background (usually 30-60 seconds).

#### Request Headers
```
Content-Type: application/json
Authorization: Bearer <token>
X-Idempotency-Key: <uuid> (optional, recommended for retries)
```

#### Request Body
```json
{
  "promptId": "550e8400-e29b-41d4-a716-446655440000",
  "optimizationInput": "Make this prompt more engaging and suitable for high school students",
  "temperature": 0.7,
  "maxTokens": 1000
}
```

**Field Descriptions:**
- `promptId` (required): UUID of the prompt to optimize
- `optimizationInput` (required): Specific instructions for optimization
- `temperature` (optional): Controls randomness (0.0-2.0). Default: 0.7
- `maxTokens` (optional): Maximum tokens for optimized output. Default: 1000

#### Response (202 Accepted)
```json
{
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440003",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "output": null,
    "errorMessage": null,
    "retryCount": 0,
    "maxRetries": 3,
    "createdAt": "2025-01-15T10:35:00Z",
    "updatedAt": "2025-01-15T10:35:00Z"
  },
  "error": null
}
```

**Status Values:**
- `PENDING`: Waiting to be processed
- `PROCESSING`: Currently being optimized by AI
- `COMPLETED`: Optimization finished successfully
- `FAILED`: Optimization failed (can retry)

#### cURL Example
```bash
curl -X POST https://api.eduprompt.com/api/prompts/optimize \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: 456e7890-e89b-12d3-a456-426614174001" \
  -d '{
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "optimizationInput": "Make it more suitable for teaching biology to 9th graders",
    "maxTokens": 800
  }'
```

---

### 2. Get Optimization Status (Polling)
**GET** `/prompts/optimize/queue/{queueId}`

Check the status of an optimization request. Poll this endpoint every 5-10 seconds until status is `COMPLETED` or `FAILED`.

#### Path Parameters
- `queueId` (required): UUID of the optimization queue entry (returned from Request Optimization)

#### Response (200 OK) - Still Processing
```json
{
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440003",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PROCESSING",
    "output": null,
    "errorMessage": null,
    "retryCount": 0,
    "maxRetries": 3,
    "createdAt": "2025-01-15T10:35:00Z",
    "updatedAt": "2025-01-15T10:35:15Z"
  },
  "error": null
}
```

#### Response (200 OK) - Completed
```json
{
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440003",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "output": "Optimized prompt text here...",
    "errorMessage": null,
    "retryCount": 0,
    "maxRetries": 3,
    "createdAt": "2025-01-15T10:35:00Z",
    "updatedAt": "2025-01-15T10:35:45Z"
  },
  "error": null
}
```

#### Response (200 OK) - Failed
```json
{
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440003",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "FAILED",
    "output": null,
    "errorMessage": "AI API timeout",
    "retryCount": 3,
    "maxRetries": 3,
    "createdAt": "2025-01-15T10:35:00Z",
    "updatedAt": "2025-01-15T10:37:30Z"
  },
  "error": null
}
```

#### Frontend Polling Example (JavaScript)
```javascript
async function pollOptimizationStatus(queueId) {
  const maxAttempts = 60; // Poll for max 5 minutes (60 * 5s)
  let attempts = 0;

  while (attempts < maxAttempts) {
    const response = await fetch(
      `https://api.eduprompt.com/api/prompts/optimize/queue/${queueId}`,
      {
        headers: { 'Authorization': `Bearer ${token}` }
      }
    );
    
    const result = await response.json();
    const status = result.data.status;

    if (status === 'COMPLETED') {
      console.log('Optimization complete:', result.data.output);
      return result.data;
    }

    if (status === 'FAILED') {
      console.error('Optimization failed:', result.data.errorMessage);
      throw new Error(result.data.errorMessage);
    }

    // Still pending/processing, wait 5 seconds
    await new Promise(resolve => setTimeout(resolve, 5000));
    attempts++;
  }

  throw new Error('Optimization timeout');
}
```

---

### 3. Get User Optimization History (Paginated)
**GET** `/prompts/optimize/history?page=0&size=20`

Get paginated optimization history for the authenticated user across all prompts.

#### Query Parameters
- `page` (optional): Page number, 0-indexed. Default: 0
- `size` (optional): Page size. Default: 20

#### Response (200 OK)
```json
{
  "data": {
    "content": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440003",
        "promptId": "550e8400-e29b-41d4-a716-446655440000",
        "status": "COMPLETED",
        "output": "Optimized prompt...",
        "retryCount": 0,
        "createdAt": "2025-01-15T10:35:00Z",
        "updatedAt": "2025-01-15T10:35:45Z"
      }
    ],
    "totalElements": 12,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "error": null
}
```

---

### 4. Get Prompt Optimization History (Paginated)
**GET** `/prompts/optimize/prompt/{promptId}/history?page=0&size=20`

Get optimization history for a specific prompt by the authenticated user.

#### Path Parameters
- `promptId` (required): UUID of the prompt

#### Query Parameters
- `page` (optional): Page number, 0-indexed. Default: 0
- `size` (optional): Page size. Default: 20

#### Response
Same structure as "Get User Optimization History"

---

### 5. Get Pending Optimizations
**GET** `/prompts/optimize/pending`

Get all pending/processing optimization requests for the authenticated user. Useful for showing "in progress" optimizations in the UI.

#### Response (200 OK)
```json
{
  "data": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440004",
      "promptId": "550e8400-e29b-41d4-a716-446655440000",
      "status": "PROCESSING",
      "output": null,
      "createdAt": "2025-01-15T10:40:00Z",
      "updatedAt": "2025-01-15T10:40:15Z"
    },
    {
      "id": "990e8400-e29b-41d4-a716-446655440005",
      "promptId": "660e8400-e29b-41d4-a716-446655440002",
      "status": "PENDING",
      "output": null,
      "createdAt": "2025-01-15T10:41:00Z",
      "updatedAt": "2025-01-15T10:41:00Z"
    }
  ],
  "error": null
}
```

---

### 6. Retry Failed Optimization
**POST** `/prompts/optimize/queue/{queueId}/retry`

Retry a failed optimization request. Only works for `FAILED` status optimizations.

#### Path Parameters
- `queueId` (required): UUID of the failed optimization queue entry

#### Response (200 OK)
```json
{
  "data": {
    "id": "770e8400-e29b-41d4-a716-446655440003",
    "promptId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "output": null,
    "errorMessage": null,
    "retryCount": 0,
    "maxRetries": 3,
    "createdAt": "2025-01-15T10:35:00Z",
    "updatedAt": "2025-01-15T10:45:00Z"
  },
  "error": null
}
```

#### Error Response (400 Bad Request)
```json
{
  "data": null,
  "error": {
    "code": "INVALID_STATUS",
    "message": ["Cannot retry optimization with status: COMPLETED"],
    "status": "BAD_REQUEST"
  }
}
```

---

### 7. Cancel/Delete Optimization
**DELETE** `/prompts/optimize/queue/{queueId}`

Cancel or delete an optimization request. Can only cancel `PENDING` or `FAILED` requests. Cannot cancel `PROCESSING` or `COMPLETED` requests.

#### Path Parameters
- `queueId` (required): UUID of the optimization queue entry

#### Response (200 OK)
```json
{
  "data": "Optimization request cancelled successfully",
  "error": null
}
```

#### Error Response (400 Bad Request)
```json
{
  "data": null,
  "error": {
    "code": "INVALID_STATUS",
    "message": ["Cannot cancel optimization with status: PROCESSING"],
    "status": "BAD_REQUEST"
  }
}
```

---

## Common Error Codes

### 400 Bad Request
Invalid request body or parameters.

```json
{
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": [
      "promptId: must not be null",
      "aiModel: must not be null"
    ],
    "status": "BAD_REQUEST"
  }
}
```

### 401 Unauthorized
Missing or invalid authentication token.

```json
{
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": ["Authentication required"],
    "status": "UNAUTHORIZED"
  }
}
```

### 403 Forbidden
User lacks permission to access the resource.

```json
{
  "data": null,
  "error": {
    "code": "FORBIDDEN",
    "message": ["You do not have permission to access this resource"],
    "status": "FORBIDDEN"
  }
}
```

### 404 Not Found
Requested resource not found.

```json
{
  "data": null,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": ["Prompt not found with id: 550e8400-e29b-41d4-a716-446655440000"],
    "status": "NOT_FOUND"
  }
}
```

### 409 Conflict
Concurrent request detected (duplicate idempotency key in progress).

```json
{
  "data": null,
  "error": {
    "code": "DUPLICATE_REQUEST",
    "message": ["Duplicate request in progress, please retry in a moment"],
    "status": "CONFLICT"
  }
}
```

### 429 Too Many Requests
Quota exceeded for the user's subscription tier.

```json
{
  "data": null,
  "error": {
    "code": "QUOTA_EXCEEDED",
    "message": ["Testing quota exceeded. Resets at 2025-01-16T00:00:00Z"],
    "status": "TOO_MANY_REQUESTS"
  }
}
```

### 500 Internal Server Error
Server-side error.

```json
{
  "data": null,
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": ["An unexpected error occurred"],
    "status": "INTERNAL_SERVER_ERROR"
  }
}
```

### 503 Service Unavailable
AI provider is temporarily unavailable.

```json
{
  "data": null,
  "error": {
    "code": "AI_PROVIDER_UNAVAILABLE",
    "message": ["OpenAI API is temporarily unavailable. Please try again later."],
    "status": "SERVICE_UNAVAILABLE"
  }
}
```

---

## Best Practices

### 1. Idempotency Keys
Always include `X-Idempotency-Key` header when making POST requests to prevent duplicate processing on network retries:

```javascript
const idempotencyKey = crypto.randomUUID();

fetch('/api/prompts/test', {
  method: 'POST',
  headers: {
    'X-Idempotency-Key': idempotencyKey,
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify(request)
});
```

### 2. Polling Strategy
For optimization requests, implement exponential backoff:

```javascript
async function pollWithBackoff(queueId) {
  let delay = 2000; // Start with 2 seconds
  const maxDelay = 10000; // Cap at 10 seconds

  while (true) {
    const status = await checkStatus(queueId);
    
    if (status === 'COMPLETED' || status === 'FAILED') {
      return status;
    }

    await new Promise(resolve => setTimeout(resolve, delay));
    delay = Math.min(delay * 1.5, maxDelay); // Exponential backoff
  }
}
```

### 3. Error Handling
Always handle quota exceeded errors gracefully:

```javascript
try {
  const result = await testPrompt(request);
} catch (error) {
  if (error.code === 'QUOTA_EXCEEDED') {
    showQuotaExceededMessage(error.resetDate);
  } else {
    showGenericError(error.message);
  }
}
```

### 4. Token Management
Monitor token usage to provide user feedback:

```javascript
const response = await testPrompt(request);
const tokensUsed = response.data.tokensUsed;
const tokenLimit = getUserTokenLimit();

if (tokensUsed > tokenLimit * 0.8) {
  showWarning('You are approaching your token limit');
}
```

---
## Version

API Version: `1.0`  
Last Updated: November 2025
