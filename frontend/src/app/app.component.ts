import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http';

interface ActionItem {
  task: string;
  owner: string;
  dueDate: string;
}

interface AnalyzeResponse {
  summary: string[];
  actionItems: ActionItem[];
  risks: string[];
  followUpEmail: string;
  pipeline: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  apiUrl = 'http://localhost:8080/api/analyze';
  transcript = '';
  language = 'it';
  loading = false;
  error = '';
  result?: AnalyzeResponse;
  copyMessage = '';

  constructor(private http: HttpClient) {}

  runAgent(): void {
    if (!this.transcript.trim()) {
      this.error = 'Inserisci una trascrizione o degli appunti.';
      return;
    }

    this.loading = true;
    this.error = '';
    this.result = undefined;

    this.http.post<AnalyzeResponse>(this.apiUrl, {
      transcript: this.transcript,
      language: this.language
    }).subscribe({
      next: (res) => {
        this.result = res;
        this.copyMessage = '';
        this.loading = false;
      },
      error: () => {
        this.error = 'Errore nella chiamata al backend. Verifica che Spring sia attivo.';
        this.copyMessage = '';
        this.loading = false;
      }
    });
  }

  useSample(): void {
    this.transcript = `Riunione progetto Alpha\n\n- Obiettivo: validare il MVP entro fine mese.\n- Rischio: dipendenza dalle API del fornitore.\n- Decisione: procedere con un mock locale per la demo.\n\nTODO: preparare demo per il 18/03 (Owner: Luca)\nAzione: aggiornare roadmap e inviarla al team entro 15/03 (Responsabile: Sara)\nNext step: definire KPI principali (Owner: Marco)`;
  }

  async copyEmail(): Promise<void> {
    if (!this.result?.followUpEmail) {
      return;
    }

    try {
      await navigator.clipboard.writeText(this.result.followUpEmail);
      this.copyMessage = 'Email copiata negli appunti.';
    } catch {
      this.fallbackCopy(this.result.followUpEmail);
    }
  }

  exportMarkdown(): void {
    if (!this.result) {
      return;
    }

    const content = this.buildMarkdown(this.result);
    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'meeting-notes-summary.md';
    link.click();
    window.URL.revokeObjectURL(url);
  }

  private buildMarkdown(result: AnalyzeResponse): string {
    const lines: string[] = [];
    lines.push('# Meeting Notes Summary');
    lines.push('');
    lines.push('## Summary');
    result.summary.forEach((item) => lines.push(`- ${item}`));
    lines.push('');
    lines.push('## Action Items');
    result.actionItems.forEach((action) => {
      const parts = [action.task];
      if (action.owner) {
        parts.push(`Owner: ${action.owner}`);
      }
      if (action.dueDate) {
        parts.push(`Scadenza: ${action.dueDate}`);
      }
      lines.push(`- ${parts.join(' - ')}`);
    });
    lines.push('');
    lines.push('## Rischi/Blocchi');
    result.risks.forEach((risk) => lines.push(`- ${risk}`));
    lines.push('');
    lines.push('## Follow-up Email');
    lines.push('');
    lines.push('```');
    lines.push(result.followUpEmail);
    lines.push('```');
    lines.push('');
    lines.push(`_Pipeline: ${result.pipeline}_`);
    return lines.join('\n');
  }

  private fallbackCopy(text: string): void {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    this.copyMessage = 'Email copiata negli appunti.';
  }
}
