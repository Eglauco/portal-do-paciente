import { HttpClient, HttpParams } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Um celular do cadastro do paciente: E.164 (vai para o link/envio) e a forma de exibição. */
export interface ConviteTelefone {
  numero: string;
  exibicao: string;
}

/** Situação do paciente para o convite do app: já tem o app? e os celulares do cadastro. */
export interface ConvitePaciente {
  temApp: boolean;
  telefones: ConviteTelefone[];
}

/** Opções do convite de vários pacientes (chave = id do paciente) + o texto (null se não configurado). */
export interface ConviteOpcoes {
  texto: string | null;
  pacientes: Record<number, ConvitePaciente>;
}

/** Resultado do envio por SMS: números (formatados) aceitos e os que falharam. */
export interface ResultadoConviteSms {
  enviados: string[];
  falhas: string[];
}

/**
 * Convite MANUAL do app nas telas de agenda/horário: busca as opções (tem app? celulares) dos pacientes de
 * uma lista de uma vez, envia o SMS de convite e registra no histórico a abertura do WhatsApp.
 */
@Service()
export class ConviteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/horario`;

  opcoes(pacienteIds: number[]): Observable<ConviteOpcoes> {
    const params = new HttpParams().set('pacienteIds', [...new Set(pacienteIds)].join(','));
    return this.http.get<ConviteOpcoes>(`${this.base}/convite/opcoes`, { params });
  }

  enviarSms(horarioId: number, telefones: string[]): Observable<ResultadoConviteSms> {
    return this.http.post<ResultadoConviteSms>(`${this.base}/${horarioId}/convite/sms`, { telefones });
  }

  registrarWhatsapp(horarioId: number, telefone: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${horarioId}/convite/whatsapp`, { telefone });
  }
}

/**
 * Situação de um paciente a partir das opções carregadas pela tela:
 * `undefined` = ainda carregando; `null` = não foi possível carregar (erro ou paciente ausente).
 */
export function conviteDoPaciente(
  opcoes: ConviteOpcoes | null | undefined,
  pacienteId: number | null | undefined,
): ConvitePaciente | null | undefined {
  if (opcoes === undefined) return undefined;
  if (opcoes === null || pacienteId == null) return null;
  return opcoes.pacientes[pacienteId] ?? null;
}

/** Link do WhatsApp (wa.me) para o número com a mensagem já preenchida. */
export function linkWhatsapp(numeroE164: string, texto: string): string {
  return `https://wa.me/${numeroE164.replace(/\D/g, '')}?text=${encodeURIComponent(texto)}`;
}
