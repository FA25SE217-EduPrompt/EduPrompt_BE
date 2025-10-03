# JWT Authentication Setup

This document describes the complete JWT authentication and authorization setup for the EduPrompt Spring Boot application.

## Components Created

### 1. DTOs
- **LoginRequest.java**: Contains email and password for login
- **RegisterRequest.java**: Contains user registration data
- **AuthenticationResponse.java**: Contains JWT token and user information

### 2. Repositories
- **UserRepository.java**: JPA repository for User entity
- **UserAuthRepository.java**: JPA repository for UserAuth entity
- **SubscriptionRepository.java**: JPA repository for Subscription entity

### 3. Services
- **CustomUserDetailsService.java**: Spring Security UserDetailsService implementation
- **JwtUtil.java**: Utility class for JWT token operations

### 4. Security Components
- **SecurityConfig.java**: Main security configuration with JWT setup
- **JwtAuthenticationFilter.java**: Filter to validate JWT tokens in requests

### 5. Controllers
- **AuthenticationController.java**: Handles login and registration endpoints
- **ProtectedController.java**: Example protected endpoints requiring authentication

### 6. Exception Handling
- **GlobalExceptionHandler.java**: Global exception handler for authentication errors

## API Endpoints

### Authentication Endpoints (Public)
- `POST /BE/api/auth/login` - User login
- `POST /BE/api/auth/register` - User registration

### Protected Endpoints (Require JWT Token)
- `GET /BE/api/hello` - Example protected endpoint
- `GET /BE/api/profile` - Get user profile information

## Usage Examples

### 1. User Registration
```bash
curl -X POST http://localhost:8080/BE/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "1234567890",
    "role": "TEACHER",
    "schoolId": null
  }'
```

### 2. User Login
```bash
curl -X POST http://localhost:8080/BE/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### 3. Access Protected Endpoint
```bash
curl -X GET http://localhost:8080/BE/api/hello \
  -H "Authorization: Bearer YOUR_JWT_TOKEN_HERE"
```

## Configuration

### JWT Settings (application.yaml)
```yaml
jwt:
  secret: "mySecretKey123456789012345678901234567890"
  expiration: 86400000 # 24 hours in milliseconds
```

## Important Notes

1. **No Password Hashing**: As requested, passwords are stored and compared as plain text. The custom PasswordEncoder in SecurityConfig simply returns the password as-is.

2. **JWT Secret**: The JWT secret key should be changed to a secure, random string in production.

3. **Token Expiration**: JWT tokens expire after 24 hours by default. This can be configured in the application.yaml file.

4. **Database Schema**: The system uses the existing User and UserAuth entities. Make sure your database has the required tables.

5. **Role-Based Access**: The system supports role-based authorization. Users can have roles like "TEACHER", "ADMIN", etc.

## Security Considerations

- Change the JWT secret key in production
- Consider implementing password hashing for better security
- Implement rate limiting for authentication endpoints
- Add input validation for all endpoints
- Consider implementing refresh tokens for better security

## Testing the Setup

1. Start the Spring Boot application
2. Register a new user using the `/api/auth/register` endpoint
3. Login with the registered user using the `/api/auth/login` endpoint
4. Use the returned JWT token to access protected endpoints
5. Test the `/api/hello` and `/api/profile` endpoints with the token
