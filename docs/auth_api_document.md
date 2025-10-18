# Auth API - Complete Documentation

Base URL: `/api/auth`

---

## Authentication Scheme

All protected endpoints require Bearer JWT token in the header:
```
Authorization: Bearer <token>
```

Token is obtained from login/register/refresh endpoints and should be stored on client (localStorage, sessionStorage, or in-memory).

---

## Response Envelope

**All responses follow this structure:**

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
- Success (2xx): `data` populated, `error` is `null`
- Error (4xx/5xx): `data` is `null`, `error` populated

**Example Success:**
```json
{
  "data": { "token": "eyJhbGciOiJIUzI1NiJ9..." },
  "error": null
}
```

**Example Error:**
```json
{
  "data": null,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "messages": ["Invalid email or password"],
    "status": "UNAUTHORIZED"
  }
}
```

---

## Endpoints

### 1. Register
Create a new user account and send verification email.

**Request:**
- Method: `POST`
- Path: `/register`
- Body:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123",
  "firstName": "Tri",
  "lastName": "Nguyen",
  "phoneNumber": "+84912345678"
}
```

**Response:**
- Status: `201`
- Data:
```json
{
  "message": "Check your email to verify your account"
}
```

**Errors:**

| Code                  | Status | Cause                                           |
|-----------------------|--------|------------------------------------------------|
| `EMAIL_ALREADY_EXISTS`| 400    | Email already registered                        |
| `INVALID_INPUT`       | 400    | Invalid email/password format or missing fields |


**Validation Rules:**
- Email: valid email format
- Password: 3-32 characters
- Phone: optional, international format (+84...) or 10+ digits
- First/Last Name: required, non-empty

**Flow:**
1. User enters registration details
2. System validates input
3. User created with `isVerified=false`, `isActive=false`
4. Verification email sent with token (24-hour expiration)
5. Frontend redirects to verification page

---

### 2. Verify Email
Activate account using token from verification email.

**Request:**
- Method: `GET`
- Path: `/verify-email`
- Query Parameters:
```
token=<verificationToken>
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Email verified successfully"
}
```

**Errors:**

| Code              | Status | Cause                     |
|-------------------|--------|---------------------------|
| `INVALID_TOKEN`   | 400    | Token invalid/malformed   |
| `USER_NOT_FOUND`  | 404    | User not found            |
| `ALREADY_VERIFIED`| 400    | Account already verified  |


**Notes:**
- Token can be expired (system allows graceful verification)
- Once verified, user can login
- Clears verification token from DB after use

**Flow:**
1. User clicks verification link from email
2. Frontend extracts token from URL
3. System verifies token and activates account
4. Frontend redirects to login

---

### 3. Resend Verification Email
Send verification email again (if first one expired or was lost).

**Request:**
- Method: `POST`
- Path: `/resend-verification`
- Query Parameters:
```
email=user@example.com
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Verification email sent to user@example.com"
}
```

**Errors:**

| Code              | Status | Cause                  |
|-------------------|--------|------------------------|
| `USER_NOT_FOUND`  | 404    | Email not registered   |
| `ALREADY_VERIFIED`| 400    | Account already verified|


**Notes:**
- New token with 24-hour expiration generated
- Old verification token invalidated
- Rate limit: max 3 resends per hour

**Flow:**
1. User clicks "resend verification email"
2. Frontend sends email address
3. System generates new token and sends email
4. User receives new verification link

---

### 4. Login
Authenticate user and receive JWT token.

**Request:**
- Method: `POST`
- Path: `/login`
- Body:
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response:**
- Status: `200`
- Data:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwicm9sZSI6IlRFQUNIRVIiLCJqdGkiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJpYXQiOjE2NzA5MzIwMDAsImV4cCI6MTY3MDkzNTYwMH0..."
}
```

**Errors:**

| Code                 | Status | Cause                                |
|----------------------|--------|--------------------------------------|
| `USER_NOT_FOUND`     | 404    | Email not registered                 |
| `INVALID_CREDENTIALS`| 401    | Wrong password                       |
| `USER_NOT_VERIFIED`  | 403    | Email not verified or account inactive|


**Token Details:**
- Expires in: 1 hour
- Contains: email, role, JTI (unique token ID)
- Use for: All protected endpoint requests

**Flow:**
1. User enters email/password
2. System validates credentials
3. System checks if account verified and active
4. JWT token generated and returned
5. Frontend stores token (localStorage/sessionStorage/memory)
6. Frontend includes token in Authorization header for subsequent requests

---

### 5. Login with Google
Authenticate using Google OAuth (SSO).

**Request:**
- Method: `POST`
- Path: `/google`
- Body:
```json
{
  "tokenId": "<Google_ID_Token>"
}
```

**Response:**
- Status: `200`
- Data:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Errors:**

| Code                       | Status | Cause                          |
|----------------------------|--------|--------------------------------|
| `INVALID_GOOGLE_TOKEN`     | 401    | Google token invalid/expired   |
| `GOOGLE_VERIFICATION_FAILED`| 401   | Token verification failed      |


**Notes:**
- If first-time login: new account created automatically
- If returning user: existing account used
- Email pre-verified (no verification email needed)
- Account automatically activated

**Flow:**
1. User clicks "Login with Google"
2. Google login flow shown (handled by Google)
3. User approves, receives Google ID token
4. Frontend sends ID token to backend
5. System verifies with Google, creates/retrieves account
6. JWT token returned
7. Frontend handles same as normal login

---

### 6. Refresh Token
Get a new access token before current one expires.

**Request:**
- Method: `POST`
- Path: `/refresh-token`
- Headers:
```
Authorization: Bearer <current_token>
```

**Response:**
- Status: `200`
- Data:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Errors:**

| Code                | Status | Cause                                   |
|---------------------|--------|-----------------------------------------|
| `INVALID_TOKEN`     | 401    | Token signature invalid                 |
| `TOKEN_ALREADY_USED`| 401    | Token already refreshed (replay attack) |
| `TOKEN_REVOKED`     | 401    | Token blacklisted or user logged out    |
| `USER_NOT_VERIFIED` | 403    | Account no longer verified              |


**Important Notes:**
- Old token automatically blacklisted (cannot be used again)
- New token has fresh 1-hour expiration
- Atomic guard prevents token replay attacks
- Can refresh up to 60 seconds after token expires

**Flow:**
1. Token expires in ~10 minutes (check `exp` claim)
2. Frontend detects expiration before making request
3. Frontend sends refresh request with old token
4. System validates, blacklists old token, issues new one
5. Frontend stores new token, updates Authorization header
6. Normal requests resume

**Recommendation for Frontend:**
- Refresh token when it's 5-10 minutes before expiration
- Or refresh on first 401 response (token expired)

---

### 7. Logout
Invalidate current token immediately.

**Request:**
- Method: `POST`
- Path: `/logout`
- Headers:
```
Authorization: Bearer <token>
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Successfully logged out"
}
```

**Errors:**

| Code                   | Status | Cause                   |
|------------------------|--------|-------------------------|
| `INVALID_TOKEN`        | 401    | Token signature invalid |
| `TOKEN_ALREADY_REVOKED`| 401    | Token already logged out|
| `USER_NOT_FOUND`       | 404    | User not found          |


**Notes:**
- Token added to Redis blacklist
- Blacklist TTL matches token expiration
- Token cannot be used after logout (even for refresh)
- All other user sessions remain valid

**Flow:**
1. User clicks logout button
2. Frontend sends token in Authorization header
3. System blacklists token in Redis
4. Frontend clears token from storage
5. Frontend redirects to login page

---

### 8. Change Password
Update password for authenticated user.

**Request:**
- Method: `POST`
- Path: `/change-password`
- Headers:
```
Authorization: Bearer <token>
```
- Body:
```json
{
  "email": "user@example.com",
  "oldPassword": "OldPass123",
  "newPassword": "NewPass456"
}
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Password changed successfully"
}
```

**Errors:**

| Code                 | Status | Cause                          |
|----------------------|--------|--------------------------------|
| `INVALID_PASSWORD`   | 400    | Old password incorrect         |
| `DUPLICATE_PASSWORD` | 400    | New password same as old       |
| `USER_NOT_FOUND`     | 404    | User not found                 |
| `UNAUTHORIZED`       | 401    | Invalid or missing token       |


**Validation Rules:**
- New password: 3-32 characters
- Must be different from old password
- No complexity requirements (kept simple for MVP)

**Notes:**
- Does NOT logout user (token remains valid)
- Old password required to verify identity
- Next token refresh will use new password

**Flow:**
1. User navigates to change password page
2. User enters old and new password
3. Frontend sends request with current token
4. System validates old password, updates hash
5. User remains logged in (no re-authentication needed)

---

### 9. Forgot Password
Initiate password reset for user without current access.

**Request:**
- Method: `POST`
- Path: `/forgot-password`
- Body:
```json
{
  "email": "user@example.com"
}
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Password reset email sent to user@example.com"
}
```

**Notes:**
- Always returns 200 (even if email doesn't exist) for security
- Email sent only if account exists
- Reset token expires in 15 minutes
- User receives email with reset link

**Flow:**
1. User clicks "Forgot password"
2. User enters email address
3. Frontend sends email to backend
4. System generates reset token (15-min expiration) and sends email
5. Frontend shows "check your email" message (regardless of whether email exists)
6. User receives email with reset link

---

### 10. Reset Password
Complete password reset using token from email.

**Request:**
- Method: `POST`
- Path: `/reset-password`
- Body:
```json
{
  "token": "<resetToken>",
  "newPassword": "NewPass789"
}
```

**Response:**
- Status: `200`
- Data:
```json
{
  "message": "Password reset successfully"
}
```

**Errors:**

| Code                 | Status | Cause                          |
|----------------------|--------|--------------------------------|
| `INVALID_TOKEN`      | 400    | Token invalid/malformed        |
| `TOKEN_EXPIRED`      | 400    | Token expired (15 min window)  |
| `DUPLICATE_PASSWORD` | 400    | New password same as current   |
| `USER_NOT_FOUND`     | 404    | User not found                 |


**Validation Rules:**
- Password: 3-32 characters
- Must be different from current password

**Notes:**
- Reset token cleared after successful reset
- User can then login with new password
- Old tokens remain valid (not automatically invalidated)

**Flow:**
1. User clicks reset link from email
2. Frontend extracts reset token from URL
3. User enters new password
4. Frontend sends token and password to backend
5. System validates token, updates password, clears token
6. Frontend redirects to login
7. User logs in with new password

---

## Authentication Flows

### Flow 1: New User Registration

```
User
  │
  ├─► POST /register (email, password, name)
  │     └─► (201) Email verification message
  │
  ├─► Check email, click verification link
  │     └─► GET /verify-email?token=xxx
  │           └─► (200) Email verified
  │
  └─► POST /login (email, password)
        └─► (200) JWT token
```

### Flow 2: Existing User Login

```
User
  │
  └─► POST /login (email, password)
        └─► (200) JWT token
```

### Flow 3: Token Refresh (Before Expiration)

```
Frontend (detects token expiring in 5-10 min)
  │
  └─► POST /refresh-token (Authorization: Bearer old_token)
        └─► (200) New JWT token
              └─ Old token automatically blacklisted
```

### Flow 4: Token Already Expired (Reactive Refresh)

```
Frontend (receives 401 on protected endpoint)
  │
  └─► POST /refresh-token (Authorization: Bearer old_token)
        └─► (200) New JWT token
              └─► Retry original request with new token
```

### Flow 5: User Logout

```
User clicks logout
  │
  └─► POST /logout (Authorization: Bearer token)
        └─► (200) Logout message
              └─ Token added to Redis blacklist
                 └─ Frontend clears token from storage
                    └─ Frontend redirects to login
```

### Flow 6: Forgot Password

```
User clicks "Forgot Password"
  │
  ├─► POST /forgot-password (email)
  │     └─► (200) Check email message
  │
  ├─► Check email, click reset link
  │     └─ Link contains reset token
  │
  ├─► Enter new password
  │     └─► POST /reset-password (token, password)
  │           └─► (200) Password reset message
  │
  └─► POST /login (email, new_password)
        └─► (200) JWT token
```

### Flow 7: Google SSO Login

```
User clicks "Login with Google"
  │
  ├─► Google login window appears
  │     └─ User enters Google credentials
  │
  ├─► User approves app access
  │     └─ Google returns ID token
  │
  └─► POST /google (tokenId)
        └─► (200) JWT token
              └─ Account auto-created if first login
```

### Flow 8: Account Verification Resend

```
User didn't receive verification email
  │
  └─► POST /resend-verification?email=user@example.com
        └─► (200) New email sent
              └─ New token with 24-hour expiration
                 └─ Old token invalidated
```

---

## Error Handling Guide

### For Frontend Developers

**HTTP Status Codes:**
- `200`: Success - process `data`
- `400`: Bad Request - validation error, show user friendly message from `error.messages`
- `401`: Unauthorized - token invalid/expired, redirect to login
- `403`: Forbidden - user not verified/inactive, show message
- `404`: Not Found - resource not found
- `500`: Server Error - retry or contact support

**Common Patterns:**

```javascript
// After login, store token
const response = await post('/api/auth/login', credentials);
if (response.error) {
  showError(response.error.messages[0]);
} else {
  localStorage.setItem('token', response.data.token);
  navigateTo('/dashboard');
}

// Make protected requests
const response = await get('/api/prompts', {
  headers: { 'Authorization': `Bearer ${token}` }
});

if (response.error?.code === 'TOKEN_REVOKED' || 
    response.error?.code === 'INVALID_TOKEN') {
  // Token expired or revoked, attempt refresh
  await refreshToken();
  // Retry request
}

// Logout
await post('/api/auth/logout', {}, {
  headers: { 'Authorization': `Bearer ${token}` }
});
localStorage.removeItem('token');
navigateTo('/login');
```

---

## Important Notes 

1. **Token Storage**: Use localStorage/sessionStorage (frontend framework preference)
2. **Token Expiration**: Check `exp` claim in JWT (decode with `jwt-decode` library)
3. **Refresh Strategy**: Refresh before expiration (5-10 min before) or on 401 response
4. **Password Requirements**: 3-32 characters (no complexity rules)
5. **Phone Format**: International format with `+` prefix or 10+ digits
6. **Email Verification**: Essential - verify before allowing login
7. **Error Messages**: Always display `error.messages` array to user
8. **CORS**: Ensure API CORS headers allow frontend origin

---

## Testing Credentials 

---
## Version

API Version: `1.0`  
Last Updated: January 2025
