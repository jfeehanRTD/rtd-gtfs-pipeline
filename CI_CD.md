# GitHub CI/CD Procedures for Software Development Teams

## Repository Setup
- Create feature branches from `main/master`
- Use meaningful branch names (`feature/user-auth`, `fix/login-bug`)
- Protect main branch with branch protection rules

## Development Workflow
1. **Branch Creation**: `git checkout -b feature/new-feature`
2. **Commit Standards**: Use conventional commits (`feat:`, `fix:`, `docs:`)
3. **Push Changes**: `git push -u origin feature/new-feature`
4. **Pull Request**: Create PR with description, reviewers, and linked issues

## Pull Request (PR) Process - Detailed

### Creating a PR
1. **Navigate to Repository**: Go to GitHub repository page
2. **Compare & Pull Request**: Click button that appears after pushing branch
3. **Fill PR Template**:
   - **Title**: Clear, descriptive summary
   - **Description**: What changes were made and why
   - **Linked Issues**: Use `Closes #123` to auto-close related issues
   - **Screenshots**: For UI changes
   - **Testing Notes**: How to test the changes

### PR Requirements & Checks
- **Automated Checks Must Pass**:
  - CI pipeline (tests, linting, build)
  - Security scans
  - Code coverage thresholds
- **Manual Requirements**:
  - Code review approvals (typically 1-2 reviewers)
  - Documentation updates if needed
  - No merge conflicts with target branch

### Review Process
**For Authors**:
- Respond to feedback promptly
- Make requested changes in new commits
- Push updates to same branch (PR auto-updates)
- Request re-review after changes

**For Reviewers**:
- Review code for functionality, style, security
- Test locally if needed
- Use GitHub's review tools:
  - **Comment**: General feedback
  - **Request Changes**: Blocks merge until addressed  
  - **Approve**: Ready to merge
- Check PR description and linked issues

### Merge Options
1. **Merge Commit**: Preserves branch history
2. **Squash and Merge**: Combines commits into single commit (recommended)
3. **Rebase and Merge**: Replays commits without merge commit

### Post-Merge Actions
- **Automatic**: Branch deletion, issue closure, deployment triggers
- **Manual**: Verify deployment, update project boards, notify stakeholders

### PR Best Practices
- Keep PRs small (< 400 lines of code)
- Use draft PRs for work-in-progress
- Add reviewers with relevant expertise
- Include tests for new functionality
- Update documentation when needed
- Use meaningful commit messages
- Resolve conflicts promptly

## Code Review Process
- Require at least 1-2 reviewer approvals
- Run automated checks (linting, tests, security scans)
- Address feedback before merging
- Use "Squash and merge" to maintain clean history

## CI/CD Pipeline
**Continuous Integration:**
- Trigger on PR creation/updates
- Run tests, linting, type checking
- Build application
- Security scanning (Dependabot, CodeQL)

**Continuous Deployment:**
- Trigger on merge to main
- Deploy to staging environment
- Run integration tests
- Deploy to production (manual approval for critical systems)

## Release Management
- Use semantic versioning (v1.2.3)
- Create release tags: `git tag v1.2.3`
- Generate release notes automatically
- Deploy specific versions to environments

## GitHub Actions Example Structure
```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run tests
      - name: Build
      - name: Deploy (if main branch)
```

## Best Practices
- Never commit directly to main
- Keep PRs small and focused
- Write descriptive commit messages
- Use environments for staging/production
- Monitor deployment health
- Implement rollback procedures