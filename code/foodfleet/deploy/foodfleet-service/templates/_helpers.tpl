{{- define "ff.labels" -}}
app: {{ required "name is required" .Values.name }}
app.kubernetes.io/name: {{ .Values.name }}
app.kubernetes.io/part-of: foodfleet
foodfleet.dev/team: {{ required "team is required" .Values.team | lower | replace " " "-" | quote }}
{{- end }}
{{- define "ff.cpus" -}}
{{- $c := toString .Values.resources.cpu -}}
{{- if hasSuffix "m" $c -}}{{ div (add (trimSuffix "m" $c | int) 999) 1000 }}{{- else -}}{{ ceil (float64 $c) | int }}{{- end -}}
{{- end }}
