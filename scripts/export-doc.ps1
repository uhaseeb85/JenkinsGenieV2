param(
  [string]$Markdown = "..\PATENT_PROPOSAL_RESTRUCTURED.md",
  [string]$OutputDocx = "..\PATENT_PROPOSAL_RESTRUCTURED.docx",
  [switch]$SkipDiagrams
)

# Fail on error
$ErrorActionPreference = 'Stop'

Write-Host "Export starting..." -ForegroundColor Cyan

# 1) Render Mermaid diagrams to SVG (unless skipped)
if (-not $SkipDiagrams) {
  Write-Host "Rendering Mermaid diagrams..." -ForegroundColor Cyan
  $diagrams = @(
    @{ In = "..\diagrams\sequence-end-to-end.mmd"; Out = "..\diagrams\sequence-end-to-end.svg" },
    @{ In = "..\diagrams\flow-file-selection.mmd"; Out = "..\diagrams\flow-file-selection.svg" },
    @{ In = "..\diagrams\sequence-validation.mmd"; Out = "..\diagrams\sequence-validation.svg" }
  )

  # Ensure mermaid-cli is installed
  $mmdc = (Get-Command mmdc -ErrorAction SilentlyContinue)
  if (-not $mmdc) {
    Write-Host "Installing @mermaid-js/mermaid-cli (requires Node.js)..." -ForegroundColor Yellow
    npm install -g @mermaid-js/mermaid-cli | Out-Null
  }

  foreach ($d in $diagrams) {
    if (Test-Path $d.In) {
      Write-Host "mmdc -i $($d.In) -o $($d.Out)" -ForegroundColor DarkGray
      mmdc -i $d.In -o $d.Out
    } else {
      Write-Warning "Missing diagram source: $($d.In)"
    }
  }
}

# 2) Convert Markdown to DOCX using Pandoc
Write-Host "Converting Markdown to DOCX with Pandoc..." -ForegroundColor Cyan
$pandoc = (Get-Command pandoc -ErrorAction SilentlyContinue)
if (-not $pandoc) {
  Write-Host "Pandoc not found. Please install from https://pandoc.org/install.html and rerun." -ForegroundColor Red
  exit 1
}

# Use GitHub-flavored Markdown and include a reference docx if provided
$pandocArgs = @(
  "-f", "gfm",
  "-t", "docx",
  "--embed-resources",
  "--standalone",
  "--resource-path=..;..\diagrams",
  "-o", $OutputDocx,
  $Markdown
)

& pandoc @pandocArgs

Write-Host "Export complete: $OutputDocx" -ForegroundColor Green
