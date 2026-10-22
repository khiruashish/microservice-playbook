# Pushing the Microservice Playbook to GitHub

Repository: <https://github.com/khiruashish/microservice-playbook> (branch `main`).

The playbook also lives inside the knowledge base, at
`~/Desktop/Git_Repos/knowledge-base/architecture/microservices/microservice-playbook`. That folder is part of the
knowledge-base repository, so **don't run `git init` inside it**. A Git repository nested in another one confuses both.
Use one of the two ways below.

## Option A: a separate clone, synced from the knowledge base (recommended)

### One time: clone the GitHub repository

```bash
mkdir -p ~/Desktop/Git_Repos
cd ~/Desktop/Git_Repos
git clone https://github.com/khiruashish/microservice-playbook.git
cd microservice-playbook
git config user.name  "Your Name"
git config user.email "you@example.com"     # an email verified on your GitHub account
```

### Every time you want to publish changes

```bash
KB=~/Desktop/Git_Repos/knowledge-base/architecture/microservices/microservice-playbook
REPO=~/Desktop/Git_Repos/microservice-playbook

# 1. Copy the knowledge-base version over the clone (keeps the clone's .git; removes files deleted in the KB)
rsync -a --delete \
  --exclude '.git/' \
  --exclude 'site/' --exclude 'target/' --exclude '.venv/' --exclude '__pycache__/' \
  --exclude 'code/chapters/*/verify/build/' \
  --exclude 'code/getting-started/*/build/' --exclude 'code/code-lab/*/build/' \
  --exclude 'code/foodfleet/*/build/' --exclude 'code/interview/*/build/' \
  "$KB/" "$REPO/"

# 2. Review, commit and push
cd "$REPO"
git pull --rebase origin main     # pick up anything committed on GitHub first
git status --short | head -50     # what changed
git add -A
git commit -m "Update the playbook: <what changed>"
git push origin main
```

The excludes match `.gitignore`. They leave out build output and the third-party source trees that some chapters
download for their measurements.

## Option B: push the folder straight from the knowledge base with `git subtree`

This works only once the folder is committed in the knowledge-base repository. Be aware that `git subtree push` also
publishes the knowledge base's **commit history** for that folder (messages, authors, dates). Use Option A if that
history should stay private.

```bash
cd ~/Desktop/Git_Repos/knowledge-base
git remote add playbook https://github.com/khiruashish/microservice-playbook.git   # once
git add architecture/microservices/microservice-playbook
git commit -m "Update the playbook"
git subtree push --prefix=architecture/microservices/microservice-playbook playbook main
```

If GitHub rejects the push because `main` already has commits that aren't in your subtree history (it was first
pushed with Option A), stay with Option A, or replace GitHub's history once with
`git push playbook "$(git subtree split --prefix=architecture/microservices/microservice-playbook)":main --force`.
The force push rewrites `main` on GitHub, so only use it if nobody else works on the repository.

## Authentication

GitHub doesn't accept account passwords for Git over HTTPS. Use one of these:

```bash
# GitHub CLI: signs in through the browser and stores the credential
brew install gh
gh auth login            # GitHub.com → HTTPS → authenticate Git with your credentials: Yes

# or SSH: generate a key, add the public key at github.com/settings/keys, use the SSH remote
ssh-keygen -t ed25519 -C "you@example.com"
pbcopy < ~/.ssh/id_ed25519.pub
git remote set-url origin git@github.com:khiruashish/microservice-playbook.git
```

A fine-grained personal access token with **Contents: read and write** on this repository also works as the password.

## Publishing the site (GitHub Pages)

The workflow `.github/workflows/docs.yml` builds the code, builds the site with `mkdocs build --strict`, and on `main`
deploys it to GitHub Pages. Turn Pages on once:

1. On GitHub, open the repository's **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **GitHub Actions**.
3. Push to `main` (or open **Actions → handbook → Run workflow**).

The site will be at `https://khiruashish.github.io/microservice-playbook/`. `mkdocs.yml` already points there:

```yaml
site_url: https://khiruashish.github.io/microservice-playbook/
repo_url: https://github.com/khiruashish/microservice-playbook
repo_name: khiruashish/microservice-playbook
edit_uri: edit/main/docs/
```

### Before relying on CI

- The `code` job runs `mvn -B -ntp verify` over every module. The Spring code in the chapters and labs was checked by
  import against the libraries' sources, **not compiled**, because Maven Central wasn't reachable where the handbook
  was written (see the Code Lab overview). Expect the first run to surface some compile errors to fix. Until then, you
  can let the site deploy anyway by deleting the line `needs: code` from the `docs` job.
- `python tools/handbook.py --check` fails the build if the nav or roadmap are out of date: run
  `python3 tools/handbook.py` locally and commit the result.

## Preview locally before pushing

```bash
cd ~/Desktop/Git_Repos/microservice-playbook
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
mkdocs serve             # http://127.0.0.1:8000
mkdocs build --strict    # the same check CI runs
```
