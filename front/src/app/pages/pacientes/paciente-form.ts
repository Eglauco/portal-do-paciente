import { Component, afterNextRender, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { NgxMaskDirective } from 'ngx-mask';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { CepService } from '../../shared/cep.service';
import { TelefoneBrDirective } from '../../shared/telefone-br.directive';
import { PacienteEntrada } from './paciente.model';
import { PacienteService } from './paciente.service';

const SEXOS = [
  { value: 'MASCULINO', label: 'Masculino' },
  { value: 'FEMININO', label: 'Feminino' },
  { value: 'OUTRO', label: 'Outro' },
  { value: 'NAO_INFORMADO', label: 'Prefiro não informar' },
];

const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
];

/** Data no formato dd/mm/aaaa: opcional, mas se preenchida precisa ser válida e não futura. */
function dataNascimentoValidator(control: AbstractControl): ValidationErrors | null {
  const v = ((control.value ?? '') as string).trim();
  if (!v) return null;
  const m = v.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!m) return { dataInvalida: true };
  const dia = +m[1];
  const mes = +m[2];
  const ano = +m[3];
  const data = new Date(ano, mes - 1, dia);
  const real = data.getFullYear() === ano && data.getMonth() === mes - 1 && data.getDate() === dia;
  if (!real || ano < 1900 || data.getTime() > Date.now()) return { dataInvalida: true };
  return null;
}

@Component({
  selector: 'app-paciente-form',
  imports: [ReactiveFormsModule, NgxMaskDirective, NgSelectModule, TelefoneBrDirective],
  templateUrl: './paciente-form.html',
  styleUrl: './paciente-form.css',
})
export class PacienteForm implements PodeSair {
  private readonly service = inject(PacienteService);
  private readonly cepService = inject(CepService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly sexos = SEXOS;
  protected readonly ufs = UFS;

  protected readonly form = new FormGroup({
    codigoIntegracao: new FormControl('', { nonNullable: true }),
    prontuario: new FormControl('', { nonNullable: true }),
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    sexo: new FormControl<string | null>(null),
    dataNascimento: new FormControl('', { nonNullable: true, validators: [dataNascimentoValidator] }),
    rg: new FormControl('', { nonNullable: true }),
    cpf: new FormControl('', { nonNullable: true }),
    cns: new FormControl('', { nonNullable: true }),
    nomeMae: new FormControl('', { nonNullable: true }),
    nomePai: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    telefone: new FormControl('', { nonNullable: true }),
    telefonesAdicionais: new FormArray<FormControl<string>>([]),
    cep: new FormControl('', { nonNullable: true }),
    rua: new FormControl('', { nonNullable: true }),
    numero: new FormControl('', { nonNullable: true }),
    bairro: new FormControl('', { nonNullable: true }),
    municipio: new FormControl('', { nonNullable: true }),
    uf: new FormControl<string | null>(null),
    complemento: new FormControl('', { nonNullable: true }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);
  protected readonly buscandoCep = signal(false);

  // Acesso ao app (o paciente ativa sozinho por OTP; aqui o admin só revoga).
  protected readonly ativo = signal(false);
  protected readonly revogando = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;
  /** Evita disparar a busca de CEP enquanto o formulário é preenchido pelo carregamento. */
  private preenchendo = false;

  constructor() {
    // Autopreenchimento de endereço ao completar o CEP (só em digitação do usuário).
    this.form.controls.cep.valueChanges.pipe(takeUntilDestroyed()).subscribe((cep) => {
      if (this.preenchendo) return;
      if ((cep ?? '').replace(/\D/g, '').length === 8) this.buscarCep();
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    // Só carrega no navegador (evita chamada sem token no SSR/prerender).
    afterNextRender(() => {
      if (this.editando() && this.codigo() != null) this.carregar(this.codigo()!);
    });
  }

  protected get telefonesAdicionais(): FormArray<FormControl<string>> {
    return this.form.controls.telefonesAdicionais;
  }

  protected adicionarTelefone(valor = ''): void {
    this.telefonesAdicionais.push(new FormControl(valor, { nonNullable: true }));
    this.form.markAsDirty();
  }

  protected removerTelefone(indice: number): void {
    this.telefonesAdicionais.removeAt(indice);
    this.form.markAsDirty();
  }

  private setTelefonesAdicionais(numeros: string[]): void {
    this.telefonesAdicionais.clear();
    numeros.forEach((n) => this.telefonesAdicionais.push(new FormControl(n, { nonNullable: true })));
  }

  private carregar(id: number): void {
    this.service.buscarPorId(id).subscribe({
      next: (p) => {
        this.preenchendo = true;
        this.form.patchValue({
          codigoIntegracao: p.codigoIntegracao ?? '',
          prontuario: p.prontuario ?? '',
          nome: p.nome,
          sexo: p.sexo ?? null,
          dataNascimento: this.isoParaData(p.dataNascimento ?? null),
          rg: p.rg ?? '',
          cpf: p.cpf ?? '',
          cns: p.cns ?? '',
          nomeMae: p.nomeMae ?? '',
          nomePai: p.nomePai ?? '',
          email: p.email ?? '',
          telefone: p.telefone ?? '',
          cep: p.cep ?? '',
          rua: p.rua ?? '',
          numero: p.numero ?? '',
          bairro: p.bairro ?? '',
          municipio: p.municipio ?? '',
          uf: p.uf ?? null,
          complemento: p.complemento ?? '',
        });
        this.setTelefonesAdicionais(p.telefonesAdicionais ?? []);
        this.preenchendo = false;
        this.ativo.set(!!p.ativo);
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  private buscarCep(): void {
    this.buscandoCep.set(true);
    this.cepService.buscar(this.form.controls.cep.value).subscribe((end) => {
      this.buscandoCep.set(false);
      if (!end) return;
      this.form.patchValue({
        rua: end.logradouro || this.form.controls.rua.value,
        bairro: end.bairro || this.form.controls.bairro.value,
        municipio: end.municipio || this.form.controls.municipio.value,
        uf: end.uf || this.form.controls.uf.value,
      });
      this.form.markAsDirty();
    });
  }

  private isoParaData(iso: string | null): string {
    const m = (iso ?? '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
  }

  private dataParaIso(valor: string): string | null {
    const m = (valor ?? '').trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: string): boolean {
    const control = this.form.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  private valores(): PacienteEntrada {
    const f = this.form.getRawValue();
    const texto = (v: string) => (v.trim() ? v.trim() : null);
    return {
      nome: f.nome.trim(),
      telefone: texto(f.telefone),
      codigoIntegracao: texto(f.codigoIntegracao),
      prontuario: texto(f.prontuario),
      sexo: (f.sexo as PacienteEntrada['sexo']) ?? null,
      dataNascimento: this.dataParaIso(f.dataNascimento),
      rg: texto(f.rg),
      cpf: texto(f.cpf),
      cns: texto(f.cns),
      nomeMae: texto(f.nomeMae),
      nomePai: texto(f.nomePai),
      email: texto(f.email),
      rua: texto(f.rua),
      numero: texto(f.numero),
      bairro: texto(f.bairro),
      municipio: texto(f.municipio),
      uf: f.uf ?? null,
      cep: texto(f.cep),
      complemento: texto(f.complemento),
      telefonesAdicionais: this.telefonesAdicionais.controls
        .map((c) => (c.value ?? '').trim())
        .filter((v) => v.length > 0),
    };
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, this.valores())
      : this.service.criar(this.valores());
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Paciente salvo');
        this.router.navigate(['/pacientes']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(this.mensagemErro(e));
      },
    });
  }

  /** Mensagem amigável a partir da resposta do backend (409 = dado único; 400 = validação). */
  private mensagemErro(e: { status?: number; error?: { message?: string } }): string {
    if (e?.status === 409) return e.error?.message ?? 'Já existe um paciente com um dos dados únicos.';
    if (e?.status === 400) return e.error?.message ?? 'Verifique os dados informados (CPF/CNS inválido?).';
    return 'Não foi possível salvar o paciente.';
  }

  protected async revogar(): Promise<void> {
    const confirmado = await this.confirmar(
      'Revogar o acesso do paciente ao app? Ele precisará de um novo código para entrar.',
    );
    if (!confirmado) return;
    this.revogando.set(true);
    this.service.revogarAcesso(this.codigo()!).subscribe({
      next: () => {
        this.ativo.set(false);
        this.revogando.set(false);
        this.toastr.success('Acesso revogado');
      },
      error: () => {
        this.revogando.set(false);
        this.toastr.error('Não foi possível revogar o acesso.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o paciente?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Paciente excluído');
        this.router.navigate(['/pacientes']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o paciente.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/pacientes']);
  }

  private confirmar(mensagem: string): Promise<boolean> {
    this.confirmacao.set(mensagem);
    return new Promise<boolean>((resolve) => {
      this.resolverConfirmacao = resolve;
    });
  }

  protected responderConfirmacao(resposta: boolean): void {
    this.confirmacao.set(null);
    this.resolverConfirmacao?.(resposta);
    this.resolverConfirmacao = null;
  }
}
