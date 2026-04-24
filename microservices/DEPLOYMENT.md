# Microservices — AWS Lambda Deployment Guide

This folder contains **3 independent Lambda microservices** that replace the Spring Boot monolith
for cloud deployment.

## Architecture

```
Frontend (S3)
      │
      ▼
API Gateway (AWS)
  ├── GET  /api/me      → Lambda: user-service
  ├── POST /api/posts   → Lambda: posts-service   (JWT required)
  └── GET  /api/posts   → Lambda: stream-service  (public)
            GET  /api/stream →  ↑ same Lambda

All services share one DynamoDB table: twitter-posts
```

| Service | Handler class | Endpoint | Auth |
|---|---|---|---|
| `user-service` | `com.twitter.lambda.user.UserHandler::handleRequest` | `GET /api/me` | ✅ JWT |
| `posts-service` | `com.twitter.lambda.posts.PostsHandler::handleRequest` | `POST /api/posts` | ✅ JWT |
| `stream-service` | `com.twitter.lambda.stream.StreamHandler::handleRequest` | `GET /api/posts`, `GET /api/stream` | ❌ Public |

---

## Step 1 — Build the fat JARs

Run this in each service folder:

```bash
cd microservices/user-service
mvn clean package -DskipTests

cd ../posts-service
mvn clean package -DskipTests

cd ../stream-service
mvn clean package -DskipTests
```

Each `target/` folder will contain a `*-shaded.jar` ready for Lambda.

---

## Step 2 — Create DynamoDB Table

In your AWS console (or CLI):

```
Table name  : twitter-posts
Primary key : id  (String)
```

No sort key needed. The services scan and sort in Java.

> **AWS CLI:**
> ```bash
> aws dynamodb create-table \
>   --table-name twitter-posts \
>   --attribute-definitions AttributeName=id,AttributeType=S \
>   --key-schema AttributeName=id,KeyType=HASH \
>   --billing-mode PAY_PER_REQUEST \
>   --region us-east-1
> ```

---

## Step 3 — Create Lambda Functions

In AWS Console → Lambda → **Create function**:

For each service:
1. Runtime: **Java 17**
2. Upload the `.jar` from `target/`
3. Handler: (see table above)
4. Memory: **512 MB** (minimum recommended for Java)
5. Timeout: **30 seconds**

### Environment variables to set in each Lambda:

**user-service:**
```
AUTH0_DOMAIN         = dev-zis6hlg4u4uwjsxd.us.auth0.com
AUTH0_AUDIENCE       = https://twitter-api/
CORS_ALLOWED_ORIGINS = https://YOUR-BUCKET.s3.amazonaws.com
```

**posts-service:**
```
AUTH0_DOMAIN         = dev-zis6hlg4u4uwjsxd.us.auth0.com
AUTH0_AUDIENCE       = https://twitter-api/
DYNAMODB_TABLE       = twitter-posts
CORS_ALLOWED_ORIGINS = https://YOUR-BUCKET.s3.amazonaws.com
```

**stream-service:**
```
DYNAMODB_TABLE       = twitter-posts
CORS_ALLOWED_ORIGINS = https://YOUR-BUCKET.s3.amazonaws.com
```

> ⚠️ Do NOT hardcode credentials. Lambda reads AWS creds from its execution role automatically.

---

## Step 4 — IAM Role for Lambdas

Each Lambda needs an execution role with these policies:
- `AWSLambdaBasicExecutionRole` (for CloudWatch logs)
- `AmazonDynamoDBFullAccess` (or a scoped policy allowing `PutItem` / `Scan` on `twitter-posts`)

The `user-service` does NOT need DynamoDB access.

---

## Step 5 — Create API Gateway

1. Go to **API Gateway** → Create **HTTP API**
2. Add routes:

| Method | Route | Integration |
|--------|-------|-------------|
| GET | `/api/me` | user-service Lambda |
| POST | `/api/posts` | posts-service Lambda |
| GET | `/api/posts` | stream-service Lambda |
| GET | `/api/stream` | stream-service Lambda |
| OPTIONS | `/{proxy+}` | (enable CORS in API GW settings) |

3. Enable **CORS** in API Gateway settings
4. **Deploy** the API → copy the Invoke URL (e.g. `https://xxxx.execute-api.us-east-1.amazonaws.com`)

---

## Step 6 — Update the Frontend

In your frontend `.env` (or S3 environment config), change:

```
VITE_API_BASE_URL = https://xxxx.execute-api.us-east-1.amazonaws.com/api
```

---

## Local Testing

You can invoke a handler locally using the AWS SAM CLI:

```bash
sam local invoke UserFunction --event events/me-event.json
```

Or simply test with curl against your deployed API Gateway URL:

```bash
# Public stream (no auth)
curl https://xxxx.execute-api.us-east-1.amazonaws.com/api/posts

# Protected endpoint (replace TOKEN with a real Auth0 access token)
curl -H "Authorization: Bearer TOKEN" \
     https://xxxx.execute-api.us-east-1.amazonaws.com/api/me
```
