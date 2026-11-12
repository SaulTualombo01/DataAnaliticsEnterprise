import {useEffect, useState} from 'react';
import {api, type PageInfo} from './api';

type QueryState<T> = {path: string | null; data?: T; info?: PageInfo; error?: unknown; loading: boolean};
type QueryOptions<T> = {
    refreshKey?: string | number;
    cache?: RequestCache;
    pollMs?: number;
    shouldPoll?: (data: T) => boolean;
};

/** Solo lecturas: cancela la consulta y su temporizador al cambiar de contexto. */
export function useApiQuery<T>(path: string | null, {
    refreshKey = 0, cache, pollMs = 0, shouldPoll
}: QueryOptions<T> = {}) {
    const [state, setState] = useState<QueryState<T>>({path, loading: path !== null});

    useEffect(() => {
        if (path === null) {
            setState({path, loading: false});
            return;
        }
        const requestPath = path;
        const controller = new AbortController();
        let timer: ReturnType<typeof setTimeout> | undefined;
        setState({path, loading: true});

        async function load() {
            try {
                const response = await api<T>(requestPath, {signal: controller.signal, cache});
                if (controller.signal.aborted) return;
                setState({path, data: response.data, info: response.meta.page_info, loading: false});
                if (pollMs > 0 && shouldPoll?.(response.data)) timer = setTimeout(load, pollMs);
            } catch (error) {
                if (!controller.signal.aborted) setState({path, error, loading: false});
            }
        }
        void load();
        return () => {controller.abort(); clearTimeout(timer);};
    }, [path, refreshKey, cache, pollMs, shouldPoll]);

    // No presentar una respuesta anterior mientras se prepara la nueva consulta.
    return state.path === path ? state : {path, loading: path !== null};
}
