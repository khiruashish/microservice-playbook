FROM scratch
COPY rootfs/ /
ENV TZ=UTC PATH=/usr/lib/postgresql/16/bin:/bin
USER 999
EXPOSE 5432
ENTRYPOINT ["/entrypoint.sh"]
