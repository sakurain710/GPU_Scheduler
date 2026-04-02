# Fix Plan (Executed)

## Scope
Repair the 4 identified integration blockers:
1. Business exception HTTP status mismatch
2. Wrong README claim about `/api/v1/**` compatibility
3. Missing CORS configuration for frontend-backend split deployment
4. Missing RBAC dictionary APIs for permissions/resources

## Steps And Status
- [x] Step 1: Fix business exception status behavior  
  Change `GlobalExceptionHandler` so `BusinessException` returns `ResponseEntity` with real HTTP status from `httpStatus`.

- [x] Step 2: Remove `/api/v1` compatibility claim from docs  
  Update README and keep only `/api/**` routes.

- [x] Step 3: Add CORS support  
  Enable `cors()` in `SecurityConfig` and provide `CorsConfigurationSource`.

- [x] Step 4: Add RBAC dictionary APIs  
  Add:
  - `GET /api/rbac/permissions`
  - `GET /api/rbac/resources`
  Implement with filters via `RbacDictionaryService`.

- [x] Step 5: Verification  
  - `mvn -q -DskipTests compile` passed
  - `mvn -q test` passed

## Output
Code changes are completed and verified, ready for frontend integration testing.
